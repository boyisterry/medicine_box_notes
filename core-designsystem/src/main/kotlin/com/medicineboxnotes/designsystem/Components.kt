package com.medicineboxnotes.designsystem

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
fun PaperCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(MBDimens.cardRadius),
        color = MBColor.Surface,
        border = androidx.compose.foundation.BorderStroke(.5.dp, MBColor.Hairline),
        shadowElevation = 2.dp,
    ) { Column(Modifier.padding(18.dp), content = content) }
}

@Composable
fun SectionLabel(title: String, english: String, action: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.width(10.dp))
            Text(english, style = MaterialTheme.typography.bodyMedium, color = MBColor.Ink3)
        }
        action?.invoke()
    }
}

@Composable
fun MemberDot(label: String, index: Int, modifier: Modifier = Modifier) {
    val pair = MemberPalette[index.mod(MemberPalette.size)]
    Box(
        modifier.size(44.dp).clip(CircleShape).background(pair.first),
        contentAlignment = Alignment.Center,
    ) { Text(label.take(1), color = pair.second, style = MaterialTheme.typography.titleLarge) }
}

@Composable
fun StatusBadge(text: String, kind: BadgeKind = BadgeKind.Info) {
    val colors = when (kind) {
        BadgeKind.Success -> MBColor.SuccessSoft to MBColor.Success
        BadgeKind.Warning -> MBColor.WarningSoft to MBColor.Warning
        BadgeKind.AI -> MBColor.AISoft to MBColor.AI
        BadgeKind.Info -> MBColor.PrimarySoft to MBColor.Primary
    }
    Text(
        text, modifier = Modifier.clip(RoundedCornerShape(50)).background(colors.first).padding(horizontal = 10.dp, vertical = 5.dp),
        color = colors.second, style = MaterialTheme.typography.labelSmall,
    )
}
enum class BadgeKind { Info, Success, Warning, AI }

@Composable
fun StockMeter(stock: Int, modifier: Modifier = Modifier, label: String = "Stock") {
    val color = when { stock <= 5 -> MBColor.Warning; stock <= 12 -> MBColor.Primary; else -> MBColor.Success }
    Column(modifier) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = MBColor.Ink3, style = MaterialTheme.typography.bodyMedium)
            Text(stock.toString(), color = color, style = MaterialTheme.typography.labelLarge)
        }
        Spacer(Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { (stock / 30f).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
            color = color, trackColor = color.copy(alpha = .15f),
        )
    }
}

@Composable
fun TodoCheckRow(title: String, subtitle: String, time: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    val bg by animateColorAsState(if (checked) MBColor.Success else Color.Transparent, label = "todo")
    Row(
        Modifier.fillMaxWidth().clickable(role = Role.Checkbox) { onChecked(!checked) }.padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(28.dp).clip(CircleShape).background(bg).border(1.dp, if (checked) MBColor.Success else MBColor.Hairline, CircleShape),
            contentAlignment = Alignment.Center,
        ) { if (checked) Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(18.dp)) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, color = MBColor.Ink3, style = MaterialTheme.typography.bodyMedium)
        }
        Text(time, color = MBColor.Ink2)
    }
}

@Composable
fun EmptyState(icon: ImageVector = Icons.Rounded.Warning, title: String, text: String, action: (@Composable () -> Unit)? = null) {
    Column(Modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, tint = MBColor.Primary, modifier = Modifier.size(38.dp))
        Spacer(Modifier.height(12.dp)); Text(title, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(6.dp)); Text(text, color = MBColor.Ink3, style = MaterialTheme.typography.bodyMedium)
        if (action != null) { Spacer(Modifier.height(16.dp)); action() }
    }
}
