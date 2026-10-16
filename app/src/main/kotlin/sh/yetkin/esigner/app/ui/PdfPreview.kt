package sh.yetkin.esigner.app.ui

import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.delay
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import sh.yetkin.esigner.app.MacGestures
import java.awt.Cursor
import kotlin.math.ceil
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isMetaPressed
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.platform.Font
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.withContext
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.rendering.PDFRenderer
import sh.yetkin.esigner.app.i18n.str
import sh.yetkin.esigner.core.StampPosition
import sh.yetkin.esigner.core.VisibleStamp
import sh.yetkin.esigner.core.defaultStampPosition
import sh.yetkin.esigner.core.stampFits
import java.io.File
import java.time.Instant
import kotlin.math.roundToInt

/**
 * The visible signature on the preview: what it shows, and where the user put it ([chosen], null for the default
 * spot). [onPlace] gets a new position, or null to go back to the default; [onResize] a new height in points.
 */
class StampPlacement(
    val stamp: VisibleStamp,
    val signerName: String,
    val icon: ImageBitmap,
    val chosen: StampPosition?,
    val editable: Boolean,
    val focused: Boolean,
    val onFocus: () -> Unit,
    val onResize: (Float) -> Unit,
    val onPlace: (StampPosition?) -> Unit,
)

/**
 * The pages of [file] one under another, each rendered when it scrolls into view, with the visible signature
 * drawn where it will go; clicking a page or dragging the box moves it. PDFBox documents are not thread-safe,
 * so every use after loading synchronizes on the document.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun PdfPreview(file: File, placement: StampPlacement?, modifier: Modifier) {
    val document by produceState<Result<LoadedPdf>?>(null, file) {
        val loaded = withContext(Dispatchers.IO) { runCatching { LoadedPdf.load(file) } }
        value = loaded
        try {
            awaitCancellation()
        } finally {
            loaded.getOrNull()?.doc?.let { doc -> withContext(NonCancellable + Dispatchers.IO) { synchronized(doc) { doc.close() } } }
        }
    }
    val pdf = document?.getOrNull()
    // The space the stamp needs whatever the signing time (default spot, fit check), and the box as drawn now.
    val size = placement?.let { p -> remember(p.stamp.label, p.stamp.image != null, p.stamp.height, p.signerName) { p.stamp.size(p.signerName) } }
    val drawSize = placement?.let { p ->
        remember(p.stamp.label, p.stamp.image != null, p.stamp.height, p.signerName) { p.stamp.size(p.signerName, Instant.now()) }
    }
    // The default spot depends on the signatures already in the document.
    val defaultPosition by produceState<StampPosition?>(null, pdf, size) {
        value = if (pdf == null || size == null) null else withContext(Dispatchers.IO) {
            runCatching { synchronized(pdf.doc) { defaultStampPosition(pdf.doc, size.first, size.second) } }.getOrNull()
        }
    }
    val position = placement?.chosen ?: defaultPosition
    val fits by produceState(true, pdf, position, size) {
        value = if (pdf == null || size == null || position == null) true else withContext(Dispatchers.IO) {
            runCatching { synchronized(pdf.doc) { stampFits(pdf.doc, position, size.first, size.second) } }.getOrDefault(true)
        }
    }
    var zoom by remember { mutableStateOf(1f) }
    val listState = rememberLazyListState()
    val hScroll = rememberScrollState()
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    // Last pointer position over the pages, in viewport pixels; zooming keeps the point under it in place.
    var pointer by remember { mutableStateOf<Offset?>(null) }
    val density = LocalDensity.current
    val pending = remember { PendingScroll() }
    // Changes the zoom around [anchor] (the viewport centre for the buttons). The new scroll lands in the same
    // layout pass as the new zoom, so no frame shows the zoomed pages at the old scroll; until that pass further
    // steps (several pinch events per frame) build on the pending position.
    val zoomTo = { target: Float, anchor: Offset? ->
        val new = target.coerceIn(ZOOM_LEVELS.first(), ZOOM_LEVELS.last())
        val doc = pdf
        if (doc != null && new != zoom && viewport != IntSize.Zero) {
            val pad = with(density) { PAGE_PADDING.toPx() }
            val gap = with(density) { PAGE_GAP.toPx() }
            val ratios = doc.pages.map { it.ratio }
            val (x, y) = zoomScroll(
                ratios = ratios,
                pageWidth = viewport.width - 2 * pad,
                gap = gap,
                pad = pad,
                viewWidth = viewport.width.toFloat(),
                from = zoom,
                to = new,
                scrollX = pending.x ?: hScroll.value.toFloat(),
                scrollY = pending.y ?: scrollTop(ratios, (viewport.width - 2 * pad) * zoom, gap, listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset),
                anchor = anchor ?: Offset(viewport.width / 2f, viewport.height / 2f),
            )
            pending.x = x
            pending.y = y
            zoom = new
            val (index, offset) = itemAt(ratios, (viewport.width - 2 * pad) * new, gap, y)
            listState.requestScrollToItem(index, offset)
        }
    }
    // Trackpad pinch arrives for the whole window; it zooms the preview while the pointer is over it.
    val currentZoomTo by rememberUpdatedState(zoomTo)
    LaunchedEffect(Unit) {
        MacGestures.pinch.collect { pointer?.let { at -> currentZoomTo(zoom * (1f + it), at) } }
    }
    // Whether the stamp was focused when the current press started; a click that only focuses does not move it.
    val wasFocused = remember { booleanArrayOf(true) }
    val currentPlacement by rememberUpdatedState(placement)
    Panel(
        modifier.pointerInput(placement != null) {
            awaitPointerEventScope {
                while (true) {
                    if (awaitPointerEvent(PointerEventPass.Initial).type == PointerEventType.Press) {
                        wasFocused[0] = currentPlacement?.focused ?: true
                        currentPlacement?.onFocus?.invoke()
                    }
                }
            }
        },
        padding = PaddingValues(0.dp),
    ) {
        Column(
            Modifier.fillMaxWidth().heightIn(min = PANEL_HEADER_HEIGHT).padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        str("preview.title"),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        if (pdf != null) str("preview.pages", file.name, pdf.pages.size) else file.name,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (placement?.chosen != null && placement.editable) {
                    TextButton(onClick = { placement.onPlace(null) }) { Text(str("preview.stamp.reset")) }
                }
                if (pdf != null) ZoomControls(zoom) { zoomTo(it, null) }
                IconButton(onClick = { openFile(file) }) {
                    Icon(AppIcons.OpenInNew, contentDescription = str("preview.open"), tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                }
            }
            if (placement != null && pdf != null && !fits) {
                Text(
                    str("preview.stamp.invalid"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 2.dp, end = 12.dp),
                )
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Box(
            Modifier.weight(1f).fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
            contentAlignment = Alignment.Center,
        ) {
            when {
                document == null -> Text(str("preview.loading"), color = MaterialTheme.colorScheme.onSurfaceVariant)
                pdf == null -> Text(
                    str("preview.failed"),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(24.dp),
                )
                else -> BoxWithConstraints(
                    Modifier
                        .fillMaxSize()
                        .onSizeChanged { viewport = it }
                        .onPointerEvent(PointerEventType.Move) { pointer = it.changes.first().position }
                        .onPointerEvent(PointerEventType.Enter) { pointer = it.changes.first().position }
                        .onPointerEvent(PointerEventType.Exit) { pointer = null }
                        // Ctrl/Cmd + scroll zooms, like in PDF readers; plain scrolling still scrolls.
                        .onPointerEvent(PointerEventType.Scroll, PointerEventPass.Initial) { event ->
                            val modifiers = event.keyboardModifiers
                            if (modifiers.isCtrlPressed || modifiers.isMetaPressed) {
                                val delta = event.changes.first().scrollDelta.y
                                if (delta != 0f) zoomTo(stepZoom(zoom, if (delta < 0) 1 else -1), event.changes.first().position)
                                event.changes.forEach { it.consume() }
                            }
                        },
                ) {
                    val pageWidth = (maxWidth - PAGE_PADDING * 2) * zoom
                    LazyColumn(
                        Modifier
                            .fillMaxHeight()
                            .layout { measurable, constraints ->
                                // Measuring updates the scroll range for the new zoom, so the pending scroll fits.
                                val placeable = measurable.measure(constraints)
                                pending.x?.let { hScroll.dispatchRawDelta(it - hScroll.value) }
                                pending.x = null
                                pending.y = null
                                layout(placeable.width, placeable.height) { placeable.place(0, 0) }
                            }
                            .horizontalScroll(hScroll)
                            .width(maxOf(maxWidth, pageWidth + PAGE_PADDING * 2)),
                        state = listState,
                        contentPadding = PaddingValues(PAGE_PADDING),
                        verticalArrangement = Arrangement.spacedBy(PAGE_GAP),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        items(pdf.pages.size) { index ->
                            val stamp = if (placement != null && size != null && position?.page == index + 1) {
                                StampOnPage(placement, drawSize!!, position, fits)
                            } else {
                                null
                            }
                            PdfPage(pdf, index, pageWidth, placement?.takeIf { size != null }, size, stamp) { wasFocused[0] }
                        }
                    }
                }
            }
        }
    }
}

/** Zoom out, the current zoom (click resets to 100%), zoom in. */
@Composable
private fun ZoomControls(zoom: Float, onZoom: (Float) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { onZoom(stepZoom(zoom, -1)) }, enabled = zoom > ZOOM_LEVELS.first()) {
            Icon(AppIcons.Remove, contentDescription = str("preview.zoomOut"), modifier = Modifier.size(18.dp))
        }
        TextButton(onClick = { onZoom(1f) }) {
            Text("${(zoom * 100).roundToInt()}%")
        }
        IconButton(onClick = { onZoom(stepZoom(zoom, 1)) }, enabled = zoom < ZOOM_LEVELS.last()) {
            Icon(Icons.Default.Add, contentDescription = str("preview.zoomIn"), modifier = Modifier.size(18.dp))
        }
    }
}

private val PAGE_PADDING = 16.dp
private val PAGE_GAP = 12.dp

private class PendingScroll {
    var x: Float? = null
    var y: Float? = null
}

/** Distance in px from the first page's top to the list's first visible item position ([index], [offset]). */
internal fun scrollTop(ratios: List<Float>, pageWidth: Float, gap: Float, index: Int, offset: Int): Float =
    (0 until index).sumOf { (pageWidth / ratios[it] + gap).toDouble() }.toFloat() + offset

/** Inverse of [scrollTop]: the item and offset that put [top] at the top of the list. */
internal fun itemAt(ratios: List<Float>, pageWidth: Float, gap: Float, top: Float): Pair<Int, Int> {
    var start = 0f
    ratios.forEachIndexed { i, ratio ->
        val end = start + pageWidth / ratio + gap
        if (top < end || i == ratios.lastIndex) return i to (top - start).coerceAtLeast(0f).roundToInt()
        start = end
    }
    return 0 to 0
}

/**
 * New horizontal scroll and [scrollTop] that keep the document point under [anchor] (viewport px) in place when
 * the zoom goes from [from] to [to]. [pageWidth] is the page width at zoom 1; pages are centred in a column
 * [pad] wide on each side, with [gap] between them. Pages scale with the zoom, padding and gaps do not.
 */
internal fun zoomScroll(
    ratios: List<Float>,
    pageWidth: Float,
    gap: Float,
    pad: Float,
    viewWidth: Float,
    from: Float,
    to: Float,
    scrollX: Float,
    scrollY: Float,
    anchor: Offset,
): Pair<Float, Float> {
    fun left(zoom: Float) = (maxOf(viewWidth, pageWidth * zoom + 2 * pad) - pageWidth * zoom) / 2
    val fractionX = (scrollX + anchor.x - left(from)) / (pageWidth * from)
    val x = (left(to) + fractionX * pageWidth * to - anchor.x)
        .coerceIn(0f, maxOf(0f, pageWidth * to + 2 * pad - viewWidth))

    // Find the page under the anchor and where on it, then put that spot back under the anchor.
    val docY = scrollY + anchor.y - pad
    var oldStart = 0f
    var newStart = 0f
    for ((i, ratio) in ratios.withIndex()) {
        val oldHeight = pageWidth * from / ratio
        val newHeight = pageWidth * to / ratio
        if (docY < oldStart + oldHeight + gap || i == ratios.lastIndex) {
            val within = (docY - oldStart).coerceAtLeast(0f)
            val newDocY = if (within <= oldHeight) newStart + within / oldHeight * newHeight else newStart + newHeight + (within - oldHeight)
            return x to (newDocY - (anchor.y - pad)).coerceAtLeast(0f)
        }
        oldStart += oldHeight + gap
        newStart += newHeight + gap
    }
    return x to scrollY
}

private val ZOOM_LEVELS = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f, 3f, 4f)

private fun stepZoom(zoom: Float, direction: Int): Float =
    if (direction > 0) ZOOM_LEVELS.firstOrNull { it > zoom + 0.01f } ?: ZOOM_LEVELS.last()
    else ZOOM_LEVELS.lastOrNull { it < zoom - 0.01f } ?: ZOOM_LEVELS.first()

private class StampOnPage(val placement: StampPlacement, val size: Pair<Float, Float>, val position: StampPosition, val fits: Boolean)

@Composable
private fun PdfPage(
    pdf: LoadedPdf,
    index: Int,
    width: Dp,
    placement: StampPlacement?,
    size: Pair<Float, Float>?,
    stamp: StampOnPage?,
    wasFocused: () -> Boolean,
) {
    val page = pdf.pages[index]
    // Rendered at the screen's pixel size (Retina included), so the page is never scaled up. Rounded up to a
    // quarter so small width changes reuse the image, and capped so a deep zoom does not exhaust memory.
    val widthPx = with(LocalDensity.current) { width.toPx() }
    val renderScale = (ceil(widthPx / page.shownWidth * 4f) / 4f).coerceIn(0.5f, MAX_RENDER_PIXELS / page.shownWidth)
    var image by remember(pdf, index) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(pdf, index, renderScale) {
        // While pinching, render only once the zoom settles; the previous image stays until then.
        if (image != null) delay(RERENDER_DELAY_MS)
        image = withContext(Dispatchers.IO) {
            runCatching { synchronized(pdf.doc) { pdf.renderer.renderImage(index, renderScale) }.toComposeImageBitmap() }.getOrNull()
        } ?: image
    }
    // Stamp coordinates are unrotated page coordinates; rotated pages keep the default spot.
    val placeable = placement != null && size != null && placement.editable && page.rotation == 0
    val onPlace by rememberUpdatedState(placement?.onPlace)
    BoxWithConstraints(
        Modifier
            .width(width)
            .aspectRatio(page.ratio)
            .background(Color.White)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant)
            .then(
                if (placeable) {
                    Modifier.handCursor().pointerInput(index, size) {
                        detectTapGestures { tap ->
                            if (!wasFocused()) return@detectTapGestures
                            val scale = this.size.width / page.width
                            onPlace?.invoke(
                                page.clamp(StampPosition(index + 1, tap.x / scale - size!!.first / 2, tap.y / scale - size.second / 2), size),
                            )
                        }
                    }
                } else {
                    Modifier
                },
            ),
    ) {
        image?.let {
            Image(
                it,
                contentDescription = str("preview.page", index + 1),
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.FillBounds,
                filterQuality = FilterQuality.High,
            )
        }
        if (stamp != null && page.rotation == 0) StampBox(stamp, page, constraints.maxWidth / page.width, placeable && stamp.placement.focused)
    }
}

/**
 * The stamp as it will look, at [scale] pixels per point. Dragging it moves it within the page; dragging the
 * corner handle resizes it (the height is one setting for all PDFs, the width follows the text).
 */
@Composable
private fun StampBox(stamp: StampOnPage, page: PageInfo, scale: Float, draggable: Boolean) {
    val (width, height) = stamp.size
    val current by rememberUpdatedState(stamp.position)
    val currentSize by rememberUpdatedState(stamp.size)
    val onPlace by rememberUpdatedState(stamp.placement.onPlace)
    val onResize by rememberUpdatedState(stamp.placement.onResize)
    val density = LocalDensity.current
    val accent = when {
        !stamp.fits -> MaterialTheme.colorScheme.error
        stamp.placement.focused -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.outlineVariant
    }
    Box(
        Modifier
            .offset { IntOffset((current.x * scale).roundToInt(), (current.y * scale).roundToInt()) }
            .size(with(density) { (width * scale).toDp() }, with(density) { (height * scale).toDp() })
            .then(
                if (draggable) {
                    Modifier.pointerHoverIcon(PointerIcon(Cursor(Cursor.MOVE_CURSOR))).pointerInput(page, scale) {
                        detectDragGestures { change, amount ->
                            change.consume()
                            onPlace(page.clamp(current.copy(x = current.x + amount.x / scale, y = current.y + amount.y / scale), currentSize))
                        }
                    }
                } else {
                    Modifier
                },
            ),
    ) {
        StampContent(stamp, scale, height, accent)
        if (draggable) {
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .offset(5.dp, 5.dp)
                    .size(10.dp)
                    .background(Color.White)
                    .border(1.5.dp, accent)
                    .pointerHoverIcon(PointerIcon(Cursor(Cursor.SE_RESIZE_CURSOR)))
                    .pointerInput(scale) {
                        detectDragGestures { change, amount ->
                            change.consume()
                            // Follows the corner diagonally: the height changes by the drag along the box's diagonal.
                            val (w, h) = currentSize
                            val grow = (amount.x * w + amount.y * h) / (w * w + h * h) * h / scale
                            onResize(h + grow)
                        }
                    },
            )
        }
    }
}

@Composable
private fun StampContent(stamp: StampOnPage, scale: Float, height: Float, accent: Color) {
    val density = LocalDensity.current
    val lines = remember(stamp.placement.stamp.label, stamp.placement.signerName) {
        stamp.placement.stamp.text(stamp.placement.signerName, Instant.now())?.lines().orEmpty()
    }
    Row(
        // DSS draws the stamp on a white background.
        Modifier.fillMaxSize().background(Color.White).border(1.dp, accent),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val visible = stamp.placement.stamp
        if (visible.image != null) {
            // The image part includes the gap to the text, as in the signed PDF.
            Box(Modifier.width(with(density) { (visible.imageWidth * scale).toDp() })) {
                Image(
                    stamp.placement.icon,
                    contentDescription = null,
                    modifier = Modifier
                        .padding(start = with(density) { (visible.iconInset * scale).toDp() })
                        .size(with(density) { (visible.iconSize * scale).toDp() }),
                )
            }
        }
        if (lines.isNotEmpty()) {
            val padding = with(density) { (visible.padding * scale).toDp() }
            val linePx = (height - 2 * visible.padding) * scale / lines.size
            val fontSize = remember(visible.label, visible.height, stamp.placement.signerName) { visible.fontSize(stamp.placement.signerName) }
            Column(Modifier.padding(padding)) {
                lines.forEach {
                    Text(
                        it,
                        color = Color.Black,
                        fontFamily = StampFont,
                        fontSize = with(density) { (fontSize * scale).toSp() },
                        lineHeight = with(density) { linePx.toSp() },
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }
        }
    }
}

/** The font the signed PDF's stamp text uses, so the preview's text is as wide as in the PDF. */
private val StampFont = FontFamily(Font("stamp-font", VisibleStamp.fontData))

/**
 * Widest page image in pixels; beyond it a zoomed page is scaled up instead of rendered larger. An A4 page this wide
 * costs about 37 MB per copy (heap, Skia, GPU texture); at 4096 px it was 95 MB.
 */
private const val MAX_RENDER_PIXELS = 2560f
private const val RERENDER_DELAY_MS = 200L

/** Crop box size in points and rotation, read up front so the UI thread never waits on a render. */
private class PageInfo(val width: Float, val height: Float, val rotation: Int) {
    /** Width on screen in points: rotated pages show their height across. */
    val shownWidth: Float get() = if (rotation % 180 == 0) width else height

    val ratio: Float
        get() = (if (rotation % 180 == 0) width / height else height / width).takeIf { it.isFinite() && it > 0f } ?: A4_RATIO

    /** Keeps a [size] stamp at [position] inside the page. */
    fun clamp(position: StampPosition, size: Pair<Float, Float>) = position.copy(
        x = position.x.coerceIn(0f, (width - size.first).coerceAtLeast(0f)),
        y = position.y.coerceIn(0f, (height - size.second).coerceAtLeast(0f)),
    )
}

private class LoadedPdf(val doc: PDDocument, val pages: List<PageInfo>) {
    val renderer = PDFRenderer(doc)

    companion object {
        fun load(file: File): LoadedPdf {
            val doc = PDDocument.load(file)
            return try {
                LoadedPdf(doc, doc.pages.map { PageInfo(it.cropBox.width, it.cropBox.height, it.rotation) })
            } catch (e: Exception) {
                doc.close()
                throw e
            }
        }
    }
}

private const val A4_RATIO = 1f / 1.414f
