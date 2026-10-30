package com.tvbox.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.tvbox.app.ui.theme.TvColors

@Composable
internal fun OptionPickerDialog(
    title: String,
    labels: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val listState = rememberLazyListState()
    val requesters = remember(labels) { List(labels.size) { FocusRequester() } }
    val closeFocus = remember { FocusRequester() }
    val selected = selectedIndex.coerceIn(0, labels.lastIndex.coerceAtLeast(0))
    val maxHeight = LocalConfiguration.current.screenHeightDp.dp * 0.78f
    Dialog(onDismissRequest = onDismiss) {
        LaunchedEffect(labels, selected) {
            if (labels.isEmpty()) closeFocus.requestFocusWhenReady()
            else {
                listState.scrollToItem(selected)
                requesters[selected].requestFocusWhenReady()
            }
        }
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = TvColors.Surface,
            border = BorderStroke(1.dp, TvColors.Border),
            modifier = Modifier.fillMaxWidth().heightIn(max = maxHeight).focusGroup(),
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth()) {
                    Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                    TextButton(onClick = onDismiss, modifier = Modifier.focusRequester(closeFocus)) { Text("关闭") }
                }
                Text("方向键选择，确认键应用，返回键取消", color = TvColors.TextSecondary,
                    style = MaterialTheme.typography.bodySmall)
                LazyColumn(
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f, fill = false),
                ) {
                    itemsIndexed(labels, key = { index, _ -> index }) { index, label ->
                        val chosen = index == selected
                        val shape = RoundedCornerShape(12.dp)
                        Button(
                            onClick = { onSelect(index) },
                            modifier = Modifier.fillMaxWidth().focusRequester(requesters[index]).tvFocusScale(shape),
                            shape = shape,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (chosen) TvColors.AccentSoft else TvColors.SurfaceRaised,
                                contentColor = if (chosen) TvColors.AccentStrong else TvColors.TextPrimary,
                            ),
                        ) {
                            Text(if (chosen) "✓ $label" else label, Modifier.weight(1f), maxLines = 2,
                                overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}
