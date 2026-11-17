@file:OptIn(ExperimentalMaterial3Api::class)

package com.bulkplan.tracker.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bulkplan.tracker.logic.PlanLogic

/** Palette taken from the mockups: deep navy, slate cards, one blue accent, coloured macros. */
object C {
    val Bg = Color(0xFF0E1525)
    val Card = Color(0xFF172036)
    val CardHi = Color(0xFF1D2945)
    val Inner = Color(0xFF121A2C)
    val Line = Color(0xFF26324D)
    val Text = Color(0xFFF1F4FA)
    val Muted = Color(0xFF94A0B8)
    val Blue = Color(0xFF3B82F6)
    val BlueSoft = Color(0xFF60A5FA)
    val BlueTint = Color(0xFF1C2D52)
    val Green = Color(0xFF22C55E)
    val Yellow = Color(0xFFFBBF24)
    val Red = Color(0xFFEF4444)
    val Purple = Color(0xFFB9B4F0)
    val Orange = Color(0xFFF59E0B)
}

@Composable
fun BulkTheme(content: @Composable () -> Unit) {
    val scheme = darkColorScheme(
        primary = C.Blue, onPrimary = Color.White, background = C.Bg, onBackground = C.Text,
        surface = C.Card, onSurface = C.Text, surfaceVariant = C.Inner, onSurfaceVariant = C.Muted,
        outline = C.Line, primaryContainer = C.BlueTint, onPrimaryContainer = C.Text,
        secondaryContainer = C.CardHi, onSecondaryContainer = C.Text, tertiary = C.Green,
        surfaceContainerHigh = C.Card, surfaceContainerHighest = C.CardHi, surfaceContainer = C.Card,
    )
    MaterialTheme(colorScheme = scheme, content = content)
}

val Big = TextStyle(fontSize = 34.sp, fontWeight = FontWeight.Bold, lineHeight = 38.sp, color = C.Text)
val H2 = TextStyle(fontSize = 21.sp, fontWeight = FontWeight.Bold, lineHeight = 26.sp, color = C.Text)
val H3 = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold, lineHeight = 22.sp, color = C.Text)

/** Centered page title with an icon on the left and Settings on the right, like the mockups. */
@Composable
fun PageHeader(title: String, icon: ImageVector, subtitle: String, onSettings: () -> Unit) {
    Column(Modifier.fillMaxWidth().background(C.Bg).padding(horizontal = 12.dp, vertical = 6.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) { Icon(icon, null, tint = C.Muted, modifier = Modifier.size(28.dp)) }
            Text(title, modifier = Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = C.Text)
            IconButton(onClick = onSettings, modifier = Modifier.size(48.dp)) { Icon(Icons.Filled.Settings, "Settings", tint = C.Muted, modifier = Modifier.size(28.dp)) }
        }
        Text(subtitle, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center, fontSize = 13.sp, color = C.Muted)
    }
}

/** The rounded slate card used everywhere. */
@Composable
fun Section(
    title: String? = null,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    iconTint: Color = Color.Unspecified,
    highlight: Boolean = false,
    action: (@Composable RowScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier.fillMaxWidth().padding(bottom = 12.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(C.Card)
            .border(if (highlight) 1.5.dp else 1.dp, if (highlight) C.Blue else C.Line, RoundedCornerShape(18.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (title != null || action != null) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) { Icon(icon, null, tint = iconTint, modifier = Modifier.size(28.dp)); Spacer(Modifier.width(12.dp)) }
            if (title != null) Text(title, style = H2, modifier = Modifier.weight(1f))
            action?.invoke(this)
        }
        content()
    }
}

@Composable
fun Muted(text: String, modifier: Modifier = Modifier, size: Int = 14) =
    Text(text, modifier = modifier, color = C.Muted, fontSize = size.sp, lineHeight = (size + 5).sp)

@Composable
fun Tag(text: String, color: Color = C.BlueSoft) {
    Box(Modifier.clip(RoundedCornerShape(8.dp)).background(color.copy(alpha = 0.15f)).padding(horizontal = 9.dp, vertical = 3.dp)) {
        Text(text, color = color, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** Full-width tinted button with a plus: the mockups' "+ Add Meal" / "+ Add Task". */
@Composable
fun AddButton(label: String, modifier: Modifier = Modifier, icon: ImageVector = Ico.Plus, onClick: () -> Unit) {
    Row(
        modifier.fillMaxWidth().height(50.dp).clip(RoundedCornerShape(14.dp)).background(C.BlueTint)
            .border(1.dp, C.Blue.copy(alpha = .45f), RoundedCornerShape(14.dp)).clickable(onClick = onClick),
        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = C.BlueSoft, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(10.dp))
        Text(label, color = C.BlueSoft, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** Small pill button, e.g. "Filter". */
@Composable
fun PillButton(label: String, icon: ImageVector? = null, selected: Boolean = false, onClick: () -> Unit) {
    Row(
        Modifier.height(44.dp).clip(RoundedCornerShape(22.dp)).background(if (selected) C.Blue else C.Card)
            .border(1.dp, if (selected) C.Blue else C.Line, RoundedCornerShape(22.dp)).clickable(onClick = onClick).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) { Icon(icon, null, tint = if (selected) Color.White else C.BlueSoft, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)) }
        Text(label, color = if (selected) Color.White else C.BlueSoft, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
    }
}

/** Round-cornered icon tile used for list rows and quick actions. */
@Composable
fun IconTile(icon: ImageVector, size: Dp = 44.dp, tint: Color = Color.Unspecified, bg: Color = Color.Transparent) {
    Box(Modifier.size(size).clip(RoundedCornerShape(12.dp)).background(bg), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(size * .72f))
    }
}

/** The big progress ring from the nutrition mockup. */
@Composable
fun Ring(fraction: Float, size: Dp, color: Color = C.Green, stroke: Dp = 14.dp, content: @Composable () -> Unit) {
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val w = stroke.toPx(); val d = this.size.width - w
            drawArc(C.Line, 0f, 360f, false, Offset(w / 2, w / 2), Size(d, d), style = Stroke(w))
            drawArc(color, -90f, 360f * fraction.coerceIn(0f, 1f), false, Offset(w / 2, w / 2), Size(d, d), style = Stroke(w, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) { content() }
    }
}

@Composable
fun Meter(fraction: Float, color: Color = C.Blue, track: Color = C.Line, height: Dp = 9.dp) {
    Box(Modifier.fillMaxWidth().height(height).clip(CircleShape).background(track)) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(height).clip(CircleShape).background(color))
    }
}

/** One macro line: icon, coloured label, value / target, bar. */
@Composable
fun MacroBar(icon: ImageVector, label: String, color: Color, value: Double, target: Double, unit: String = "g") {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = Color.Unspecified, modifier = Modifier.size(30.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, color = color, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text(PlanLogic.fmtN(Math.rint(value)), color = C.Text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text(" / ${PlanLogic.fmtN(Math.rint(target))} $unit", color = C.Text, fontSize = 15.sp)
            }
            Spacer(Modifier.height(6.dp))
            Meter(if (target > 0) (value / target).toFloat() else 0f, color = color)
        }
    }
}

@Composable
fun fieldColors(): TextFieldColors = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = C.Blue, unfocusedBorderColor = C.Line, focusedContainerColor = C.Inner, unfocusedContainerColor = C.Inner,
    focusedLabelColor = C.BlueSoft, unfocusedLabelColor = C.Muted, cursorColor = C.Blue,
    focusedTextColor = C.Text, unfocusedTextColor = C.Text, focusedPlaceholderColor = C.Muted, unfocusedPlaceholderColor = C.Muted,
)

/** A text field for a number that saves on every change without moving the cursor. */
@Composable
fun NumField(label: String, value: Double?, key: Any, modifier: Modifier = Modifier, hint: String? = null, onChange: (Double?) -> Unit) {
    var text by remember(key) { mutableStateOf(value?.let { if (it == Math.rint(it)) it.toLong().toString() else it.toString() } ?: "") }
    OutlinedTextField(
        value = text,
        onValueChange = { v -> text = v; onChange(v.replace(",", "").toDoubleOrNull()) },
        label = { Text(label) },
        placeholder = if (hint != null) { { Text(hint) } } else null,
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        colors = fieldColors(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier,
    )
}

@Composable
fun TextIn(label: String, value: String, key: Any, modifier: Modifier = Modifier, singleLine: Boolean = true, minLines: Int = 1, onChange: (String) -> Unit) {
    var text by remember(key) { mutableStateOf(value) }
    OutlinedTextField(
        value = text, onValueChange = { text = it; onChange(it) }, label = { Text(label) },
        singleLine = singleLine, minLines = minLines, modifier = modifier, shape = RoundedCornerShape(12.dp), colors = fieldColors(),
    )
}

/** Plain controlled field with the app's styling. */
@Composable
fun Field(
    value: String, onChange: (String) -> Unit, label: String, modifier: Modifier = Modifier, number: Boolean = false,
    singleLine: Boolean = true, minLines: Int = 1, visual: VisualTransformation = VisualTransformation.None,
) {
    OutlinedTextField(
        value = value, onValueChange = onChange, label = { Text(label) }, singleLine = singleLine, minLines = minLines,
        modifier = modifier, shape = RoundedCornerShape(12.dp), colors = fieldColors(), visualTransformation = visual,
        keyboardOptions = if (number) KeyboardOptions(keyboardType = KeyboardType.Decimal) else KeyboardOptions.Default,
    )
}

@Composable
fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected, onClick = onClick, label = { Text(label) }, shape = RoundedCornerShape(20.dp),
        colors = FilterChipDefaults.filterChipColors(
            containerColor = C.Inner, labelColor = C.Text, selectedContainerColor = C.Blue, selectedLabelColor = Color.White,
        ),
        border = FilterChipDefaults.filterChipBorder(enabled = true, selected = selected, borderColor = C.Line, selectedBorderColor = C.Blue),
    )
}

@Composable
fun ScaleRow(label: String, value: Int?, onPick: (Int?) -> Unit) {
    Column {
        Muted(label)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            (1..5).forEach { n -> Chip("$n", value == n) { onPick(if (value == n) null else n) } }
        }
    }
}

/** Round check button: green when done. */
@Composable
fun CheckCircle(done: Boolean, size: Dp = 32.dp, onClick: () -> Unit) {
    Box(
        Modifier.size(size).clip(CircleShape).background(if (done) C.Green else Color.Transparent)
            .border(2.dp, if (done) C.Green else C.Muted.copy(alpha = .6f), CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { if (done) Icon(Ico.Check, "Done", tint = Color.White, modifier = Modifier.size(size * .6f)) }
}
