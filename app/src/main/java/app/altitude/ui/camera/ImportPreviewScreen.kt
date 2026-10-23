package app.altitude.ui.camera

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.altitude.R
import app.altitude.domain.AccessAction
import app.altitude.domain.AltitudeUnit
import app.altitude.domain.CaptionAnchor
import app.altitude.domain.ImportNoticeKind
import app.altitude.domain.WatermarkStyle
import app.altitude.domain.accessAction
import app.altitude.domain.grantsToRequest
import app.altitude.domain.importNoticeKind
import app.altitude.gallery.currentMediaGrants
import app.altitude.gallery.permissionName
import app.altitude.gallery.PhotoFrames
import app.altitude.i18n.rememberLocalFormat
import app.altitude.ui.theme.Outfit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val CanvasColor = Color(0xFF090A0C)
private val Cream = Color(0xFFF4F1EA)
private val Ink = Color(0xFF141414)
private val Muted = Color(0xFFB7B3AA)
private val Card = Color(0xFF171614)

@Composable
fun ImportPreviewScreen(
    uris: List<Uri>,
    style: WatermarkStyle,
    anchor: CaptionAnchor,
    unit: AltitudeUnit,
    showPlace: Boolean,
    onStyle: (WatermarkStyle) -> Unit,
    onAnchor: (CaptionAnchor) -> Unit,
    onClose: () -> Unit,
    onSaved: (Int, String?) -> Unit,
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val format = rememberLocalFormat()
    val language = resources.configuration.locales[0]
    val files = remember { ImportFiles(context) }
    val scope = rememberCoroutineScope()
    var accessGeneration by remember { mutableIntStateOf(0) }
    var pages by remember(uris) { mutableStateOf(uris.map { ImportedPage(it) }) }
    var saving by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    val frames = remember { mutableStateMapOf<String, Bitmap>() }
    val retained = remember { mutableListOf<Bitmap>() }
    val pagesState = rememberUpdatedState(pages)
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        accessGeneration += 1
    }

    DisposableEffect(files) {
        onDispose {
            (retained + frames.values + pagesState.value.mapNotNull { it.preview }).forEach { bitmap ->
                if (!bitmap.isRecycled) bitmap.recycle()
            }
            frames.clear()
            files.deleteAll()
        }
    }

    LaunchedEffect(uris, accessGeneration, showPlace, language) {
        retained += frames.values
        pagesState.value.forEach { page -> page.preview?.let { retained += it } }
        frames.clear()
        pages = uris.map { ImportedPage(it) }
        files.deleteAll()
        pages = loadImportedPages(
            context = context,
            files = files,
            uris = uris,
            showPlace = showPlace,
            locale = language,
            onPage = { pages = it },
        )
    }

    val pagerState = rememberPagerState(pageCount = { pages.size.coerceAtLeast(1) })
    LaunchedEffect(pages, style, anchor, unit, showPlace, language.toLanguageTag(), pagerState.currentPage) {
        val order = listOf(pagerState.currentPage) + pages.indices.filter { it != pagerState.currentPage }
        val keep = mutableSetOf<String>()
        for (index in order) {
            val page = pages.getOrNull(index) ?: continue
            val capture = page.capture ?: continue
            val altitude = page.altitude ?: continue
            val preview = page.preview ?: continue
            val key = frameKey(index, page, style, anchor, unit, showPlace)
            keep += key
            if (frames[key] == null) {
                val content = format.importWatermark(capture, altitude, page.place, showPlace, unit)
                frames[key] = withContext(Dispatchers.Default) {
                    WatermarkRenderer.render(preview, content, style, anchor)
                }
            }
        }
        val stale = frames.keys.filter { it !in keep }
        if (stale.isNotEmpty()) {
            delay(80)
            stale.forEach { key ->
                frames.remove(key)?.let { bitmap -> if (!bitmap.isRecycled) bitmap.recycle() }
            }
        }
    }

    val pipeline = remember { PhotoPipeline(context) }
    suspend fun saveIndex(index: Int): String {
        val page = pagesState.value[index]
        val capture = page.capture ?: throw CaptureException(R.string.error_photo_decode)
        val altitude = page.altitude ?: throw CaptureException(R.string.error_photo_decode)
        val file = page.file ?: throw CaptureException(R.string.error_photo_decode)
        val full = withContext(Dispatchers.IO) {
            PhotoFrames.decodeUpright(file, capture.orientation, IMPORT_SAVE_EDGE)
        } ?: throw CaptureException(R.string.error_photo_decode)
        return try {
            val content = format.importWatermark(capture, altitude, page.place, showPlace, unit)
            pipeline.saveFramedCopy(
                upright = full,
                content = content,
                style = style,
                anchor = anchor,
                saved = SavedExif(
                    latitude = capture.latitude,
                    longitude = capture.longitude,
                    altitudeMeters = altitude.meters,
                    clock = capture.clock,
                    fallbackEpochMs = capture.clock?.instant()?.toInstant()?.toEpochMilli()
                        ?: System.currentTimeMillis(),
                ),
                nameIndex = index,
            )
        } finally {
            if (full !== page.preview && !full.isRecycled) full.recycle()
        }
    }

    fun failureText(error: Throwable): String {
        val res = (error as? CaptureException)?.messageRes ?: R.string.error_save_failed
        return resources.getString(res)
    }

    val currentIndex = pagerState.currentPage.coerceIn(0, pages.lastIndex.coerceAtLeast(0))
    val current = pages.getOrNull(currentIndex)
    ImportPreviewScaffold(
        pageLabel = if (pages.size > 1) {
            stringResource(R.string.gallery_page, currentIndex + 1, pages.size)
        } else {
            null
        },
        loading = current?.capture == null && current?.errorRes == null,
        errorRes = current?.errorRes,
        notice = importNoticeKind(
            hasCoordinates = current?.capture?.hasCoordinates == true,
            altitude = current?.altitude,
            locationRedacted = current?.locationRedacted == true,
        ),
        showNotice = current?.capture != null,
        resolving = current?.capture?.hasCoordinates == true && current.altitude == null && current.errorRes == null,
        style = style,
        anchor = anchor,
        canSave = current?.ready == true && !saving,
        canSaveAll = pages.count(ImportedPage::ready) > 1 && !saving,
        multiple = pages.size > 1,
        saving = saving,
        status = status,
        onStyle = onStyle,
        onAnchor = onAnchor,
        onClose = onClose,
        onAllowLocations = {
            val sdk = Build.VERSION.SDK_INT
            val grants = currentMediaGrants(context)
            if (accessAction(sdk, grants) == AccessAction.REQUEST) {
                val names = grantsToRequest(sdk).map { permissionName(it) }.toTypedArray()
                permission.launch(names)
            } else {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                        .setData(Uri.fromParts("package", context.packageName, null)),
                )
            }
        },
        onSave = {
            if (saving || current?.ready != true) return@ImportPreviewScaffold
            scope.launch {
                saving = true
                status = resources.getString(R.string.import_saving)
                val result = runCatching { saveIndex(currentIndex) }
                saving = false
                result.fold(
                    onSuccess = { uri ->
                        status = null
                        onSaved(R.string.save_success, uri)
                    },
                    onFailure = { status = failureText(it) },
                )
            }
        },
        onSaveAll = {
            if (saving) return@ImportPreviewScaffold
            scope.launch {
                saving = true
                val ready = pagesState.value.mapIndexedNotNull { index, page -> if (page.ready) index else null }
                var savedUri: String? = null
                var failed = false
                ready.forEachIndexed { ordinal, index ->
                    status = resources.getString(R.string.import_saving_progress, ordinal + 1, ready.size)
                    val result = runCatching { saveIndex(index) }
                    if (result.isSuccess) savedUri = result.getOrNull() else failed = true
                }
                saving = false
                if (savedUri != null) onSaved(R.string.save_success, savedUri)
                status = if (failed) resources.getString(R.string.error_save_failed) else null
            }
        },
        photo = {
            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize().testTag("import_pager")) { index ->
                val page = pages[index]
                val framed = frames[frameKey(index, page, style, anchor, unit, showPlace)]
                PageImage(
                    bitmap = framed ?: page.preview,
                    framed = framed != null,
                    loading = page.capture == null && page.errorRes == null,
                    errorRes = page.errorRes,
                )
            }
        },
    )
}

@Composable
internal fun ImportPreviewScaffold(
    pageLabel: String?,
    loading: Boolean,
    errorRes: Int?,
    notice: ImportNoticeKind,
    showNotice: Boolean,
    resolving: Boolean,
    style: WatermarkStyle,
    anchor: CaptionAnchor,
    canSave: Boolean,
    canSaveAll: Boolean,
    multiple: Boolean,
    saving: Boolean,
    status: String?,
    onStyle: (WatermarkStyle) -> Unit,
    onAnchor: (CaptionAnchor) -> Unit,
    onClose: () -> Unit,
    onAllowLocations: () -> Unit,
    onSave: () -> Unit,
    onSaveAll: () -> Unit,
    modifier: Modifier = Modifier,
    image: ImageBitmap? = null,
    photo: (@Composable BoxScope.() -> Unit)? = null,
) {
    val closeDescription = stringResource(R.string.cd_close_import)
    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(CanvasColor)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .testTag("import_preview"),
    ) {
        val landscape = maxWidth > maxHeight
        val sideWidth = if (maxWidth < 700.dp) 268.dp else 300.dp
        val controlCap = maxHeight * 0.5f
        Column(Modifier.fillMaxSize()) {
            ImportHeader(pageLabel, closeDescription, onClose)
            if (landscape) {
                Row(Modifier.weight(1f).fillMaxWidth()) {
                    ImportPhotoPane(
                        loading = loading,
                        errorRes = errorRes,
                        image = image,
                        photo = photo,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                    Column(
                        Modifier
                            .width(sideWidth)
                            .fillMaxHeight()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        ImportDetails(
                            notice = notice,
                            showNotice = showNotice,
                            resolving = resolving,
                            style = style,
                            anchor = anchor,
                            canSave = canSave,
                            canSaveAll = canSaveAll,
                            multiple = multiple,
                            saving = saving,
                            status = status,
                            stacked = true,
                            onStyle = onStyle,
                            onAnchor = onAnchor,
                            onAllowLocations = onAllowLocations,
                            onSave = onSave,
                            onSaveAll = onSaveAll,
                        )
                    }
                }
            } else {
                ImportPhotoPane(
                    loading = loading,
                    errorRes = errorRes,
                    image = image,
                    photo = photo,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )
                Column(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = controlCap)
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = 8.dp),
                ) {
                    ImportDetails(
                        notice = notice,
                        showNotice = showNotice,
                        resolving = resolving,
                        style = style,
                        anchor = anchor,
                        canSave = canSave,
                        canSaveAll = canSaveAll,
                        multiple = multiple,
                        saving = saving,
                        status = status,
                        stacked = false,
                        onStyle = onStyle,
                        onAnchor = onAnchor,
                        onAllowLocations = onAllowLocations,
                        onSave = onSave,
                        onSaveAll = onSaveAll,
                    )
                }
            }
        }
    }
}

@Composable
private fun ImportHeader(pageLabel: String?, closeDescription: String, onClose: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(
            text = stringResource(R.string.gallery_close),
            color = Cream,
            fontFamily = Outfit,
            fontWeight = FontWeight.Medium,
            fontSize = 15.sp,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .clip(RoundedCornerShape(999.dp))
                .semantics { contentDescription = closeDescription }
                .clickable(onClick = onClose)
                .padding(horizontal = 14.dp, vertical = 10.dp),
        )
        Text(
            text = stringResource(R.string.import_title),
            color = Cream,
            fontFamily = Outfit,
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp,
            modifier = Modifier.align(Alignment.Center),
        )
        if (pageLabel != null) {
            Text(
                text = pageLabel,
                color = Muted,
                fontFamily = Outfit,
                fontSize = 13.sp,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 12.dp),
            )
        }
    }
}

@Composable
private fun ImportPhotoPane(
    loading: Boolean,
    errorRes: Int?,
    image: ImageBitmap?,
    photo: (@Composable BoxScope.() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Box(modifier.padding(horizontal = 12.dp, vertical = 4.dp), contentAlignment = Alignment.Center) {
        when {
            photo != null -> photo()
            image != null -> {
                Image(
                    bitmap = image,
                    contentDescription = stringResource(R.string.cd_import_preview),
                    modifier = Modifier.fillMaxSize().testTag("import_photo"),
                    contentScale = ContentScale.Fit,
                )
            }
            errorRes != null -> {
                Text(
                    text = stringResource(errorRes),
                    color = Muted,
                    fontFamily = Outfit,
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(24.dp),
                )
            }
            loading -> {
                Text(
                    text = stringResource(R.string.import_reading),
                    color = Muted,
                    fontFamily = Outfit,
                    fontSize = 15.sp,
                )
            }
        }
    }
}

@Composable
private fun PageImage(bitmap: Bitmap?, framed: Boolean, loading: Boolean, errorRes: Int?) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        when {
            bitmap != null -> {
                val tag = if (framed) {
                    "import_photo"
                } else {
                    "import_plain"
                }
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = stringResource(R.string.cd_import_preview),
                    modifier = Modifier.fillMaxSize().testTag(tag),
                    contentScale = ContentScale.Fit,
                )
            }
            errorRes != null -> {
                Text(
                    text = stringResource(errorRes),
                    color = Muted,
                    fontFamily = Outfit,
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(24.dp),
                )
            }
            loading -> {
                Text(
                    text = stringResource(R.string.import_reading),
                    color = Muted,
                    fontFamily = Outfit,
                    fontSize = 15.sp,
                )
            }
        }
    }
}

@Composable
private fun ImportDetails(
    notice: ImportNoticeKind,
    showNotice: Boolean,
    resolving: Boolean,
    style: WatermarkStyle,
    anchor: CaptionAnchor,
    canSave: Boolean,
    canSaveAll: Boolean,
    multiple: Boolean,
    saving: Boolean,
    status: String?,
    stacked: Boolean,
    onStyle: (WatermarkStyle) -> Unit,
    onAnchor: (CaptionAnchor) -> Unit,
    onAllowLocations: () -> Unit,
    onSave: () -> Unit,
    onSaveAll: () -> Unit,
) {
    if (stacked) {
        Column(Modifier.fillMaxHeight()) {
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
            ) {
                ImportStatus(notice, showNotice, resolving, onAllowLocations, inset = false)
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                StyleChoices(style, onStyle, vertical = true)
                AnchorChoices(anchor, onAnchor, vertical = true, pill = true)
            }
            Spacer(Modifier.height(12.dp))
            ImportActions(canSave, canSaveAll, multiple, saving, status, onSave, onSaveAll)
        }
    } else {
        ImportStatus(notice, showNotice, resolving, onAllowLocations, inset = true)
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            StyleChoices(style, onStyle, vertical = false, wrap = true)
            AnchorChoices(anchor, onAnchor, vertical = false, pill = true)
            ImportActions(canSave, canSaveAll, multiple, saving, status, onSave, onSaveAll)
        }
    }
}

@Composable
private fun ImportStatus(
    notice: ImportNoticeKind,
    showNotice: Boolean,
    resolving: Boolean,
    onAllowLocations: () -> Unit,
    inset: Boolean,
) {
    if (showNotice && notice != ImportNoticeKind.NONE) {
        ImportNoticeCard(notice, onAllowLocations, inset)
        Spacer(Modifier.height(12.dp))
    } else if (resolving) {
        Text(
            text = stringResource(R.string.import_reading),
            color = Muted,
            fontFamily = Outfit,
            fontSize = 13.sp,
            modifier = Modifier.padding(horizontal = if (inset) 16.dp else 0.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun ImportActions(
    canSave: Boolean,
    canSaveAll: Boolean,
    multiple: Boolean,
    saving: Boolean,
    status: String?,
    onSave: () -> Unit,
    onSaveAll: () -> Unit,
) {
    val saveDescription = stringResource(R.string.cd_import_save)
    Text(
        text = stringResource(if (saving) R.string.import_saving else R.string.import_save),
        color = Ink,
        fontFamily = Outfit,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(999.dp))
            .background(Cream.copy(alpha = if (canSave) 1f else 0.38f))
            .semantics { contentDescription = saveDescription }
            .clickable(enabled = canSave, onClick = onSave)
            .padding(horizontal = 12.dp, vertical = 12.dp),
    )
    if (multiple) {
        Spacer(Modifier.height(8.dp))
        val allDescription = stringResource(R.string.cd_import_save_all)
        Text(
            text = stringResource(R.string.import_save_all),
            color = Cream.copy(alpha = if (canSaveAll) 1f else 0.38f),
            fontFamily = Outfit,
            fontWeight = FontWeight.Medium,
            fontSize = 15.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(999.dp))
                .background(Color.White.copy(alpha = 0.08f))
                .semantics { contentDescription = allDescription }
                .clickable(enabled = canSaveAll, onClick = onSaveAll)
                .padding(horizontal = 12.dp, vertical = 12.dp),
        )
    }
    val message = status
    if (message != null) {
        Text(
            text = message,
            color = Muted,
            fontFamily = Outfit,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
        )
    }
}

@Composable
private fun ImportNoticeCard(kind: ImportNoticeKind, onAllowLocations: () -> Unit, inset: Boolean) {
    val title = stringResource(kind.titleRes())
    val body = stringResource(kind.bodyRes())
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = if (inset) 16.dp else 0.dp)
            .testTag("import_notice")
            .clip(RoundedCornerShape(16.dp))
            .background(Card)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text(
            text = title,
            color = Cream,
            fontFamily = Outfit,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
        )
        Text(
            text = body,
            color = Muted,
            fontFamily = Outfit,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            modifier = Modifier.padding(top = 4.dp),
        )
        if (kind == ImportNoticeKind.LOCATION_HIDDEN) {
            val action = stringResource(R.string.import_allow_photo_locations)
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
                    .clickable(onClick = onAllowLocations)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
    }
}

private fun ImportNoticeKind.titleRes(): Int = when (this) {
    ImportNoticeKind.NONE -> R.string.import_title
    ImportNoticeKind.NO_LOCATION -> R.string.import_no_location_title
    ImportNoticeKind.LOCATION_HIDDEN -> R.string.import_location_hidden_title
    ImportNoticeKind.COMPUTED_ALTITUDE -> R.string.import_computed_title
    ImportNoticeKind.ALTITUDE_MISSING -> R.string.import_missing_title
}

private fun ImportNoticeKind.bodyRes(): Int = when (this) {
    ImportNoticeKind.NONE -> R.string.import_reading
    ImportNoticeKind.NO_LOCATION -> R.string.import_no_location_body
    ImportNoticeKind.LOCATION_HIDDEN -> R.string.import_location_hidden_body
    ImportNoticeKind.COMPUTED_ALTITUDE -> R.string.import_altitude_computed
    ImportNoticeKind.ALTITUDE_MISSING -> R.string.import_altitude_missing
}

private fun frameKey(
    index: Int,
    page: ImportedPage,
    style: WatermarkStyle,
    anchor: CaptionAnchor,
    unit: AltitudeUnit,
    showPlace: Boolean,
): String {
    val altitude = page.altitude
    return listOf(
        index,
        style.name,
        anchor.name,
        unit.name,
        showPlace,
        page.place.orEmpty(),
        altitude?.provenance?.name.orEmpty(),
        altitude?.meters?.toString().orEmpty(),
        page.capture?.latitude?.toString().orEmpty(),
        page.capture?.longitude?.toString().orEmpty(),
        page.capture?.clock?.local?.toString().orEmpty(),
    ).joinToString("|")
}
