package app.altitude.ui.camera

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Image
import app.altitude.R
import app.altitude.domain.AccessAction
import app.altitude.domain.LibraryAccess
import app.altitude.domain.accessAction
import app.altitude.domain.canReadOriginalLocation
import app.altitude.domain.grantsToRequest
import app.altitude.domain.libraryAccess
import app.altitude.gallery.currentMediaGrants
import app.altitude.gallery.loadImportThumb
import app.altitude.gallery.permissionName
import app.altitude.gallery.queryRecentPhotos
import app.altitude.ui.theme.Outfit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val CanvasColor = Color(0xFF090A0C)
private val Cream = Color(0xFFF4F1EA)
private val Ink = Color(0xFF141414)
private val Muted = Color(0xFFB7B3AA)
private val Card = Color(0xFF171614)

internal data class ImportChooserSample(
    val access: LibraryAccess,
    val locationGranted: Boolean,
    val count: Int,
    val selected: Set<Int> = emptySet(),
)

@Composable
internal fun ImportChooser(
    onClose: () -> Unit,
    onPicked: (List<Uri>) -> Unit,
    sample: ImportChooserSample? = null,
) {
    val context = LocalContext.current
    val sdk = Build.VERSION.SDK_INT
    var generation by remember { mutableIntStateOf(0) }
    val grants = remember(generation, sample) {
        if (sample == null) currentMediaGrants(context) else emptySet()
    }
    val access = sample?.access ?: libraryAccess(sdk, grants)
    val locationGranted = sample?.locationGranted ?: canReadOriginalLocation(sdk, grants)
    var photos by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var loading by remember { mutableStateOf(sample == null && access != LibraryAccess.NONE) }
    var selected by remember { mutableStateOf(setOf<Uri>()) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        generation += 1
    }
    val browse = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { picked ->
        if (picked.isNotEmpty()) onPicked(picked.take(IMPORT_LIMIT))
    }
    LaunchedEffect(access, generation, sample) {
        if (sample != null || access == LibraryAccess.NONE) {
            photos = emptyList()
            loading = false
            return@LaunchedEffect
        }
        loading = true
        photos = withContext(Dispatchers.IO) { queryRecentPhotos(context.contentResolver) }
        loading = false
    }
    val selectedCount = sample?.selected?.size ?: selected.size
    val requestAccess = {
        if (sample == null) {
            val action = accessAction(sdk, currentMediaGrants(context))
            if (action == AccessAction.REQUEST) {
                val names = grantsToRequest(sdk).map { permissionName(it) }.toTypedArray()
                ask.launch(names)
            } else {
                context.startActivity(settingsIntent(context.packageName))
            }
        }
    }
    Column(
        Modifier
            .fillMaxSize()
            .background(CanvasColor)
            .testTag("import_chooser"),
    ) {
        ChooserHeader(selectedCount, onClose)
        if (access == LibraryAccess.PARTIAL) {
            ChooserBanner(
                title = stringResource(R.string.import_partial_title),
                body = stringResource(R.string.import_partial_body),
                action = stringResource(R.string.import_allow_photos),
                onAction = requestAccess,
            )
        } else if (access == LibraryAccess.FULL && !locationGranted) {
            ChooserBanner(
                title = stringResource(R.string.import_location_off_title),
                body = stringResource(R.string.import_location_off_body),
                action = stringResource(R.string.import_allow_photos),
                onAction = requestAccess,
            )
        }
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            when {
                access == LibraryAccess.NONE && sample == null -> NeedsAccess(requestAccess)
                sample != null -> SampleGrid(sample)
                loading -> {
                    Text(
                        text = stringResource(R.string.import_reading),
                        color = Muted,
                        fontFamily = Outfit,
                        fontSize = 15.sp,
                    )
                }
                photos.isEmpty() -> {
                    Text(
                        text = stringResource(R.string.import_chooser_empty),
                        color = Muted,
                        fontFamily = Outfit,
                        fontSize = 15.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 28.dp),
                    )
                }
                else -> {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        items(photos, key = { it.toString() }) { uri ->
                            LibraryCell(
                                uri = uri,
                                selected = uri in selected,
                                onToggle = {
                                    selected = toggleUri(selected, uri)
                                },
                            )
                        }
                    }
                }
            }
        }
        ChooserActions(
            enabled = selectedCount > 0,
            onBrowse = { browse.launch(arrayOf("image/*")) },
            onUse = { if (selected.isNotEmpty()) onPicked(selected.toList()) },
        )
    }
}

@Composable
private fun ChooserHeader(selectedCount: Int, onClose: () -> Unit) {
    val closeDescription = stringResource(R.string.cd_close_import)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = stringResource(R.string.gallery_close),
            color = Cream,
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
            text = stringResource(R.string.import_chooser_title),
            color = Cream,
            fontFamily = Outfit,
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp,
        )
        Text(
            text = stringResource(R.string.gallery_page, selectedCount, IMPORT_LIMIT),
            color = Muted,
            fontFamily = Outfit,
            fontSize = 13.sp,
            modifier = Modifier.padding(end = 12.dp),
        )
    }
}

@Composable
private fun ChooserBanner(title: String, body: String, action: String, onAction: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Card)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text(text = title, color = Cream, fontFamily = Outfit, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        Text(
            text = body,
            color = Muted,
            fontFamily = Outfit,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            modifier = Modifier.padding(top = 4.dp),
        )
        Text(
            text = action,
            color = Ink,
            fontFamily = Outfit,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            modifier = Modifier
                .padding(top = 10.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(Cream)
                .testTag("import_allow_photos")
                .clickable(onClick = onAction)
                .padding(horizontal = 14.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun NeedsAccess(onAllow: () -> Unit) {
    val action = stringResource(R.string.import_allow_photos)
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 28.dp)) {
        Text(
            text = stringResource(R.string.import_needs_access_body),
            color = Muted,
            fontFamily = Outfit,
            fontSize = 15.sp,
            textAlign = TextAlign.Center,
            lineHeight = 21.sp,
        )
        Text(
            text = action,
            color = Ink,
            fontFamily = Outfit,
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp,
            modifier = Modifier
                .padding(top = 18.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(Cream)
                .testTag("import_allow_photos")
                .clickable(onClick = onAllow)
                .padding(horizontal = 22.dp, vertical = 12.dp),
        )
    }
}

@Composable
private fun SampleGrid(sample: ImportChooserSample) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items(sample.count) { index ->
            val color = SampleColors[index % SampleColors.size]
            Box(
                Modifier
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(color)
                    .then(selectionBorder(index in sample.selected)),
            )
        }
    }
}

@Composable
private fun LibraryCell(uri: Uri, selected: Boolean, onToggle: () -> Unit) {
    val context = LocalContext.current
    var bitmap by remember(uri) { mutableStateOf<ImageBitmap?>(null) }
    val description = stringResource(R.string.cd_import_choice)
    LaunchedEffect(uri) {
        val decoded = withContext(Dispatchers.IO) {
            loadImportThumb(context.contentResolver, uri, edge = 256)
        }
        bitmap = decoded?.asImageBitmap()
    }
    Box(
        Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(12.dp))
            .background(Card)
            .then(selectionBorder(selected))
            .semantics { contentDescription = description }
            .clickable(onClick = onToggle),
        contentAlignment = Alignment.Center,
    ) {
        val image = bitmap
        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        }
    }
}

@Composable
private fun ChooserActions(enabled: Boolean, onBrowse: () -> Unit, onUse: () -> Unit) {
    val browseDescription = stringResource(R.string.cd_import_browse)
    val useDescription = stringResource(R.string.cd_import_use_selected)
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(
            text = stringResource(R.string.import_use_selected),
            color = Ink,
            fontFamily = Outfit,
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(999.dp))
                .background(Cream.copy(alpha = if (enabled) 1f else 0.38f))
                .testTag("import_use_selected")
                .semantics { contentDescription = useDescription }
                .clickable(enabled = enabled, onClick = onUse)
                .padding(horizontal = 12.dp, vertical = 12.dp),
        )
        Text(
            text = stringResource(R.string.import_browse),
            color = Cream,
            fontFamily = Outfit,
            fontWeight = FontWeight.Medium,
            fontSize = 15.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .clip(RoundedCornerShape(999.dp))
                .semantics { contentDescription = browseDescription }
                .clickable(onClick = onBrowse)
                .padding(vertical = 10.dp),
        )
    }
}

private fun selectionBorder(selected: Boolean): Modifier {
    if (!selected) return Modifier
    return Modifier.border(2.dp, Cream, RoundedCornerShape(12.dp))
}

private fun toggleUri(selected: Set<Uri>, uri: Uri): Set<Uri> {
    if (uri in selected) return selected - uri
    if (selected.size >= IMPORT_LIMIT) return selected
    return selected + uri
}

private fun settingsIntent(packageName: String): Intent {
    return Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        .setData(Uri.fromParts("package", packageName, null))
}

private val SampleColors = listOf(
    Color(0xFF1B3348),
    Color(0xFFC4874A),
    Color(0xFF4C6E8A),
    Color(0xFF8FA4B5),
    Color(0xFF2A2118),
    Color(0xFF6E4B3A),
)
