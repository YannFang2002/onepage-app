package com.onepage.companion.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.onepage.companion.ui.theme.onePageColors

/** Soft press feedback used on every tappable surface. */
fun Modifier.pressScale(source: MutableInteractionSource, pressedScale: Float = 0.975f): Modifier {
    return this
}

@Composable
fun rememberPressScale(interaction: MutableInteractionSource): Float {
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.975f else 1f,
        animationSpec = tween(120),
        label = "press",
    )
    return scale
}

@Composable
fun OnePageCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    padding: androidx.compose.ui.unit.Dp = 18.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = onePageColors
    val shape = RoundedCornerShape(24.dp)
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = shape,
        color = c.card,
        border = BorderStroke(1.dp, c.hairline),
        shadowElevation = 0.dp,
        onClick = onClick ?: {},
        enabled = onClick != null,
    ) {
        Column(Modifier.padding(padding), content = content)
    }
}

@Composable
fun SectionLabel(
    text: String,
    modifier: Modifier = Modifier,
    trailing: String? = null,
) {
    val c = onePageColors
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = c.ink3,
            modifier = Modifier.weight(1f),
        )
        if (trailing != null) {
            Text(text = trailing, style = MaterialTheme.typography.labelMedium, color = c.ink3)
        }
    }
}

@Composable
fun Hairline(modifier: Modifier = Modifier, inset: androidx.compose.ui.unit.Dp = 0.dp) {
    Box(
        modifier
            .fillMaxWidth()
            .padding(horizontal = inset)
            .height(1.dp)
            .background(onePageColors.hairline)
    )
}

enum class PillTone { Neutral, Accent, Warning, Danger }

@Composable
fun Pill(
    text: String,
    tone: PillTone = PillTone.Neutral,
    modifier: Modifier = Modifier,
) {
    val c = onePageColors
    val (bg, fg) = when (tone) {
        PillTone.Neutral -> c.cardSunken to c.ink2
        PillTone.Accent -> c.accentSoft to c.accent
        PillTone.Warning -> c.amberSoft to c.amber
        PillTone.Danger -> c.dangerSoft to c.danger
    }
    Box(
        modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(text, style = MaterialTheme.typography.labelMedium, color = fg, maxLines = 1)
    }
}

@Composable
fun SignalBars(
    bars: Int,
    modifier: Modifier = Modifier,
    activeColor: Color = onePageColors.accent,
) {
    val inactive = onePageColors.hairline
    Row(modifier, verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(2.5.dp)) {
        repeat(4) { i ->
            val h = 5.dp + (i * 3.5).dp
            Box(
                Modifier
                    .width(3.dp)
                    .height(h)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (i < bars) activeColor else inactive)
            )
        }
    }
}

@Composable
fun StatCell(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    hint: String? = null,
    valueColor: Color = onePageColors.ink,
) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = onePageColors.ink3)
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                value,
                style = MaterialTheme.typography.headlineSmall,
                color = valueColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (hint != null) {
                Spacer(Modifier.width(3.dp))
                Text(
                    hint,
                    style = MaterialTheme.typography.labelMedium,
                    color = onePageColors.ink3,
                    modifier = Modifier.padding(bottom = 3.dp),
                )
            }
        }
    }
}

@Composable
fun IconBubble(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    tint: Color = onePageColors.accent,
    background: Color = onePageColors.accentSoft,
    size: androidx.compose.ui.unit.Dp = 40.dp,
) {
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.34f))
            .background(background),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.48f))
    }
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 56.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        IconBubble(icon, size = 56.dp, tint = onePageColors.ink3, background = onePageColors.cardSunken)
        Spacer(Modifier.height(18.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, color = onePageColors.ink)
        Spacer(Modifier.height(6.dp))
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = onePageColors.ink3,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        if (action != null) {
            Spacer(Modifier.height(18.dp))
            action()
        }
    }
}

@Composable
fun Dot(imageColor: Color = onePageColors.accent, size: androidx.compose.ui.unit.Dp = 7.dp) {
    Box(Modifier.size(size).clip(CircleShape).background(imageColor))
}

@Composable
fun LoadingRow(text: String = "读取中…") {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 28.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(18.dp),
            strokeWidth = 2.dp,
            color = onePageColors.accent,
        )
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = onePageColors.ink2)
    }
}

/** Compact key/value line used inside cards. */
@Composable
fun InfoLine(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = onePageColors.ink,
) {
    Row(
        modifier.fillMaxWidth().padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = onePageColors.ink2, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = valueColor, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun AlphaBox(alpha: Float, content: @Composable () -> Unit) {
    Box(Modifier.alpha(alpha)) { content() }
}

/** Human readable byte size shared by the file list and the update screen. */
fun formatSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val kb = bytes / 1024.0
    if (kb < 1) return "$bytes B"
    val mb = kb / 1024.0
    if (mb < 1) return String.format("%.0f KB", kb)
    val gb = mb / 1024.0
    return if (gb < 1) String.format("%.1f MB", mb) else String.format("%.2f GB", gb)
}

/** Hands a URL to the browser / download manager; used by the update flow. */
fun openUrl(context: android.content.Context, url: String) {
    val launched = runCatching {
        context.startActivity(
            android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }.isSuccess
    if (!launched) {
        android.widget.Toast.makeText(context, "没有可用的浏览器，请手动访问 $url", android.widget.Toast.LENGTH_LONG)
            .show()
    }
}

/**
 * Opens the system Wi-Fi list (Android 10+ quick panel when available) so the
 * user can finish a join the platform refused to automate.
 */
fun openWifiPanel(context: android.content.Context) {
    val attempts = buildList {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            add(android.content.Intent(android.provider.Settings.Panel.ACTION_WIFI))
        }
        add(android.content.Intent(android.provider.Settings.ACTION_WIFI_SETTINGS))
    }
    for (intent in attempts) {
        val opened = runCatching {
            context.startActivity(
                intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }.isSuccess
        if (opened) return
    }
    android.widget.Toast.makeText(context, "无法打开 Wi-Fi 设置，请到系统设置里手动连接", android.widget.Toast.LENGTH_LONG)
        .show()
}

/** Opens this app's permission page, used when a join is blocked by permissions. */
fun openAppSettings(context: android.content.Context) {
    val intent = android.content.Intent(
        android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        android.net.Uri.fromParts("package", context.packageName, null),
    ).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
    val opened = runCatching { context.startActivity(intent) }.isSuccess
    if (!opened) {
        android.widget.Toast.makeText(context, "无法打开应用设置", android.widget.Toast.LENGTH_SHORT).show()
    }
}
