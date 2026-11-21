package com.lifeorganizer.app.ui

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex

/**
 * A non-lazy column whose items can be reordered by long-pressing the modifier passed to [itemContent]
 * (usually applied to a drag handle) and dragging. [onReorder] receives the new order once the drag ends.
 */
@Composable
fun <T> ReorderableColumn(
    items: List<T>,
    keyOf: (T) -> String,
    onReorder: (List<T>) -> Unit,
    modifier: Modifier = Modifier,
    itemContent: @Composable (item: T, dragModifier: Modifier, dragging: Boolean) -> Unit,
) {
    val list = remember(items) { items.toMutableStateList() }
    val heights = remember { mutableStateMapOf<String, Int>() }
    var draggingKey by remember { mutableStateOf<String?>(null) }
    var offset by remember { mutableFloatStateOf(0f) }
    val currentOnReorder by rememberUpdatedState(onReorder)

    fun swapIfNeeded() {
        val k = draggingKey ?: return
        val idx = list.indexOfFirst { keyOf(it) == k }
        if (idx < 0) return
        if (offset > 0 && idx < list.lastIndex) {
            val h = heights[keyOf(list[idx + 1])] ?: return
            if (offset > h / 2f) {
                val item = list.removeAt(idx)
                list.add(idx + 1, item)
                offset -= h
            }
        } else if (offset < 0 && idx > 0) {
            val h = heights[keyOf(list[idx - 1])] ?: return
            if (-offset > h / 2f) {
                val item = list.removeAt(idx)
                list.add(idx - 1, item)
                offset += h
            }
        }
    }

    Column(modifier) {
        for (item in list) {
            val k = keyOf(item)
            key(k) {
            val isDragging = draggingKey == k
            val dragModifier = Modifier.pointerInput(k, list) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { draggingKey = k; offset = 0f },
                    onDrag = { change, amount ->
                        change.consume()
                        offset += amount.y
                        swapIfNeeded()
                    },
                    onDragEnd = {
                        draggingKey = null
                        offset = 0f
                        currentOnReorder(list.toList())
                    },
                    onDragCancel = {
                        draggingKey = null
                        offset = 0f
                    },
                )
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .onSizeChanged { heights[k] = it.height }
                    .zIndex(if (isDragging) 1f else 0f)
                    .graphicsLayer { translationY = if (isDragging) offset else 0f }
                    .then(if (isDragging) Modifier.shadow(6.dp) else Modifier),
            ) {
                itemContent(item, dragModifier, isDragging)
            }
            }
        }
    }
}
