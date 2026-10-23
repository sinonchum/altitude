package app.altitude.ui.camera

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.core.content.ContextCompat
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.altitude.R
import app.altitude.gallery.decodeAlbumBitmap
import app.altitude.gallery.deleteConsentSender
import app.altitude.gallery.openAlbumPhoto
import app.altitude.gallery.shareAlbumPhoto
import app.altitude.ui.theme.Outfit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun GalleryScreen(
    uris: List<String>,
    notice: Int?,
    onClose: () -> Unit,
    onDelete: (String) -> Boolean,
    onRefresh: () -> Unit,
    onMessage: (Int) -> Unit,
    onImport: (List<Uri>) -> Unit,
) {
    val context = LocalContext.current
    val pagerState = rememberPagerState(pageCount = { uris.size.coerceAtLeast(1) })
    var pendingDelete by remember { mutableStateOf<String?>(null) }
    val deleteLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { onRefresh() }
    val shareTitle = stringResource(R.string.gallery_share)
    val closeDescription = stringResource(R.string.cd_close_gallery)
    val importDescription = stringResource(R.string.cd_import_photo)
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(MAX_IMPORT),
    ) { picked ->
        if (picked.isNotEmpty()) onImport(picked)
    }
    val locationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        picker.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly))
    }
    val launchImport = {
        val needsLocation = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_MEDIA_LOCATION) !=
            PackageManager.PERMISSION_GRANTED
        if (needsLocation) {
            locationPermission.launch(Manifest.permission.ACCESS_MEDIA_LOCATION)
        } else {
            picker.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly))
        }
    }

    LaunchedEffect(uris.size) {
        if (uris.isEmpty()) return@LaunchedEffect
        val target = pagerState.currentPage.coerceIn(0, uris.lastIndex)
        if (pagerState.currentPage != target) pagerState.scrollToPage(target)
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF090A0C))
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.gallery_close),
                    color = Color(0xFFF3F0E8),
                    fontFamily = Outfit,
                    fontWeight = FontWeight.Medium,
                    fontSize = 15.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .semantics { contentDescription = closeDescription }
                        .clickable(onClick = onClose)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                )
                Text(
                    text = if (uris.isEmpty()) {
                        stringResource(R.string.gallery_title)
                    } else {
                        stringResource(R.string.gallery_page, pagerState.currentPage + 1, uris.size)
                    },
                    color = Color(0xFFB7B3AA),
                    fontFamily = Outfit,
                    fontSize = 14.sp,
                )
                Text(
                    text = stringResource(R.string.gallery_import),
                    color = Color(0xFF141414),
                    fontFamily = Outfit,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(Color(0xFFF4F1EA))
                        .testTag("gallery_import")
                        .semantics { contentDescription = importDescription }
                        .clickable(onClick = launchImport)
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                if (uris.isEmpty()) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(horizontal = 28.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.gallery_empty),
                            color = Color(0xFFB7B3AA),
                            fontFamily = Outfit,
                            fontSize = 15.sp,
                            textAlign = TextAlign.Center,
                        )
                        Text(
                            text = stringResource(R.string.gallery_import),
                            color = Color(0xFF141414),
                            fontFamily = Outfit,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp,
                            modifier = Modifier
                                .padding(top = 18.dp)
                                .clip(RoundedCornerShape(999.dp))
                                .background(Color(0xFFF4F1EA))
                                .semantics { contentDescription = importDescription }
                                .clickable(onClick = launchImport)
                                .padding(horizontal = 22.dp, vertical = 12.dp),
                        )
                    }
                } else {
                    HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                        AlbumPage(uris[page])
                    }
                }
            }
            val message = notice
            if (message != null) {
                Text(
                    text = stringResource(message),
                    color = Color(0xFF141414),
                    fontFamily = Outfit,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(bottom = 8.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(Color(0xFFF4F1EA))
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
            if (uris.isNotEmpty()) {
                val current = uris[pagerState.currentPage.coerceIn(0, uris.lastIndex)]
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                ) {
                    GalleryAction(stringResource(R.string.gallery_import), importDescription, launchImport)
                    GalleryAction(stringResource(R.string.gallery_share), stringResource(R.string.cd_share_photo)) {
                        runCatching { shareAlbumPhoto(context, Uri.parse(current), shareTitle) }
                            .onFailure { onMessage(R.string.error_share_failed) }
                    }
                    GalleryAction(stringResource(R.string.gallery_delete), stringResource(R.string.cd_delete_photo)) {
                        pendingDelete = current
                    }
                    GalleryAction(stringResource(R.string.gallery_open), stringResource(R.string.cd_open_gallery)) {
                        runCatching { openAlbumPhoto(context, Uri.parse(current)) }
                            .onFailure { onMessage(R.string.error_open_failed) }
                    }
                }
            }
        }
    }

    val doomed = pendingDelete
    if (doomed != null) {
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.gallery_delete_title)) },
            text = { Text(stringResource(R.string.gallery_delete_body)) },
            confirmButton = {
                TextButton(onClick = {
                    pendingDelete = null
                    val removed = try {
                        onDelete(doomed)
                    } catch (error: SecurityException) {
                        val sender = deleteConsentSender(context, Uri.parse(doomed), error)
                        if (sender == null) {
                            onMessage(R.string.error_delete_failed)
                        } else {
                            deleteLauncher.launch(IntentSenderRequest.Builder(sender).build())
                        }
                        null
                    }
                    if (removed == false) onMessage(R.string.error_delete_failed)
                }) {
                    Text(stringResource(R.string.gallery_delete_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(stringResource(R.string.gallery_delete_cancel))
                }
            },
        )
    }
}

@Composable
private fun AlbumPage(uri: String) {
    val context = LocalContext.current
    var bitmap by remember(uri) { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(uri) {
        bitmap = withContext(Dispatchers.IO) {
            runCatching { decodeAlbumBitmap(context, Uri.parse(uri), 2048) }.getOrNull()
        }
    }
    val shown = bitmap
    if (shown == null) {
        Box(Modifier.fillMaxSize())
    } else {
        Image(
            bitmap = shown.asImageBitmap(),
            contentDescription = null,
            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
            contentScale = ContentScale.Fit,
        )
    }
}

@Composable
private fun GalleryAction(label: String, description: String, onClick: () -> Unit) {
    Text(
        text = label,
        color = Color(0xFFF3F0E8),
        fontFamily = Outfit,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        maxLines = 1,
        softWrap = false,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(Color.White.copy(alpha = 0.08f))
            .semantics { contentDescription = description }
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    )
}

private const val MAX_IMPORT = 12
