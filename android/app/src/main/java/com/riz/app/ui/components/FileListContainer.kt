package com.riz.app.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val ScrollbarWidth = 3.dp
private val ScrollbarSidePadding = 4.dp
private const val MIN_THUMB_HEIGHT_PX = 40f
private const val SCROLLBAR_ALPHA = 0.4f

@Composable
fun FileListContainer(
    modifier: Modifier = Modifier,
    content: LazyListScope.() -> Unit,
) {
    val state = rememberLazyListState()
    val showScrollbar by remember(state) {
        derivedStateOf { state.canScrollForward || state.canScrollBackward }
    }
    val scrollbarColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = SCROLLBAR_ALPHA)

    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.large,
    ) {
        LazyColumn(
            state = state,
            modifier =
                Modifier
                    .fillMaxSize()
                    .drawScrollbar(state, scrollbarColor, showScrollbar),
            contentPadding = PaddingValues(vertical = 4.dp, horizontal = 4.dp),
            content = content,
        )
    }
}

private fun Modifier.drawScrollbar(
    state: LazyListState,
    color: Color,
    visible: Boolean,
): Modifier =
    drawWithContent {
        drawContent()
        if (!visible) return@drawWithContent
        val layoutInfo = state.layoutInfo
        val visibleItems = layoutInfo.visibleItemsInfo
        if (visibleItems.isEmpty()) return@drawWithContent
        val viewportHeight = (layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset).toFloat()
        if (viewportHeight <= 0f) return@drawWithContent
        val avgItemSize = visibleItems.sumOf { it.size }.toFloat() / visibleItems.size
        if (avgItemSize <= 0f) return@drawWithContent
        val totalContentHeight = layoutInfo.totalItemsCount * avgItemSize
        if (totalContentHeight <= viewportHeight) return@drawWithContent

        val firstVisible = visibleItems.first()
        val scrollOffsetPx = firstVisible.index * avgItemSize - firstVisible.offset.toFloat()

        val scrollbarWidthPx = ScrollbarWidth.toPx()
        val sidePaddingPx = ScrollbarSidePadding.toPx()
        val thumbHeight =
            (viewportHeight * viewportHeight / totalContentHeight)
                .coerceAtLeast(MIN_THUMB_HEIGHT_PX)
        val maxThumbTop = viewportHeight - thumbHeight
        val thumbTop =
            (scrollOffsetPx / (totalContentHeight - viewportHeight) * maxThumbTop)
                .coerceIn(0f, maxThumbTop)

        drawRoundRect(
            color = color,
            topLeft = Offset(size.width - scrollbarWidthPx - sidePaddingPx, thumbTop),
            size = Size(scrollbarWidthPx, thumbHeight),
            cornerRadius = CornerRadius(scrollbarWidthPx / 2),
        )
    }
