package com.onepage.companion.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.onepage.companion.ui.theme.onePageColors

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmText: String = "确定",
    dismissText: String = "取消",
    destructive: Boolean = false,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val c = onePageColors
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.card,
        shape = MaterialTheme.shapes.extraLarge,
        title = { Text(title, style = MaterialTheme.typography.titleLarge, color = c.ink) },
        text = { Text(message, style = MaterialTheme.typography.bodyMedium, color = c.ink2) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmText, color = if (destructive) c.danger else c.accent)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(dismissText, color = c.ink3)
            }
        },
    )
}

@Composable
fun PromptDialog(
    title: String,
    label: String,
    initial: String = "",
    placeholder: String = "",
    confirmText: String = "确定",
    keyboardType: KeyboardType = KeyboardType.Text,
    password: Boolean = false,
    helper: String? = null,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val c = onePageColors
    var value by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.card,
        shape = MaterialTheme.shapes.extraLarge,
        title = { Text(title, style = MaterialTheme.typography.titleLarge, color = c.ink) },
        text = {
            Column {
                OnePageTextField(
                    value = value,
                    onValueChange = { value = it },
                    label = label,
                    placeholder = placeholder,
                    keyboardType = keyboardType,
                    password = password,
                )
                if (helper != null) {
                    Spacer(Modifier.height(10.dp))
                    Text(helper, style = MaterialTheme.typography.bodySmall, color = c.ink3)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(value) }, enabled = value.isNotBlank()) {
                Text(confirmText, color = if (value.isNotBlank()) c.accent else c.ink3)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消", color = c.ink3) }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FormSheet(
    title: String,
    subtitle: String? = null,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = onePageColors.card,
        scrimColor = Color.Black.copy(alpha = 0.32f),
        dragHandle = null,
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Column(Modifier.padding(horizontal = 22.dp).padding(top = 26.dp, bottom = 28.dp)) {
            Text(title, style = MaterialTheme.typography.headlineSmall, color = onePageColors.ink)
            if (subtitle != null) {
                Spacer(Modifier.height(4.dp))
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = onePageColors.ink3)
            }
            Spacer(Modifier.height(20.dp))
            content()
        }
    }
}

@Composable
fun DialogActions(
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    enabled: Boolean = true,
    destructive: Boolean = false,
) {
    val c = onePageColors
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        TextButton(onClick = onDismiss) { Text("取消", color = c.ink3) }
        Spacer(Modifier.width(6.dp))
        TextButton(onClick = onConfirm, enabled = enabled) {
            Text(confirmText, color = when {
                !enabled -> c.ink3
                destructive -> c.danger
                else -> c.accent
            })
        }
    }
}
