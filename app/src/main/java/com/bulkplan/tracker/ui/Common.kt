@file:OptIn(ExperimentalMaterial3Api::class)

package com.bulkplan.tracker.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

val Accent = Color(0xFFC8102E)
val AccentDark = Color(0xFFFF5566)
val Ok = Color(0xFF1B7A4B)

@Composable
fun BulkTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val scheme = if (dark) darkColorScheme(
        primary = AccentDark, onPrimary = Color.White, background = Color(0xFF101112), surface = Color(0xFF191B1D),
        onBackground = Color(0xFFECEBE6), onSurface = Color(0xFFECEBE6), surfaceVariant = Color(0xFF222528),
        onSurfaceVariant = Color(0xFF9DA0A6), outline = Color(0xFF2A2D30), primaryContainer = Color(0xFF3A1C21),
        onPrimaryContainer = Color(0xFFFFD9DD), secondaryContainer = Color(0xFF2A2D30), tertiary = Color(0xFF4CC38A),
    ) else lightColorScheme(
        primary = Accent, onPrimary = Color.White, background = Color(0xFFF3F2EE), surface = Color.White,
        onBackground = Color(0xFF141517), onSurface = Color(0xFF141517), surfaceVariant = Color(0xFFEBEAE4),
        onSurfaceVariant = Color(0xFF5B5E64), outline = Color(0xFFDFDDD5), primaryContainer = Color(0xFFFBE8EB),
        onPrimaryContainer = Color(0xFF5C0010), secondaryContainer = Color(0xFFEBEAE4), tertiary = Ok,
    )
    MaterialTheme(colorScheme = scheme, content = content)
}

val Big = TextStyle(fontSize = 34.sp, fontWeight = FontWeight.Black, letterSpacing = (-0.5).sp, lineHeight = 36.sp)
val H2 = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.Bold, lineHeight = 26.sp)
val H3 = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.Bold, lineHeight = 22.sp)

@Composable
fun Section(title: String? = null, modifier: Modifier = Modifier, action: (@Composable RowScope.() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth().padding(bottom = 12.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (title != null || action != null) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (title != null) Text(title, style = H2, modifier = Modifier.weight(1f))
                action?.invoke(this)
            }
            content()
        }
    }
}

@Composable
fun Muted(text: String, modifier: Modifier = Modifier) =
    Text(text, modifier = modifier, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp, lineHeight = 19.sp)

@Composable
fun Tag(text: String, color: Color = MaterialTheme.colorScheme.primary) {
    Box(Modifier.clip(RoundedCornerShape(6.dp)).background(color.copy(alpha = 0.12f)).padding(horizontal = 8.dp, vertical = 2.dp)) {
        Text(text, color = color, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

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
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier,
    )
}

@Composable
fun TextIn(label: String, value: String, key: Any, modifier: Modifier = Modifier, singleLine: Boolean = true, minLines: Int = 1, onChange: (String) -> Unit) {
    var text by remember(key) { mutableStateOf(value) }
    OutlinedTextField(
        value = text, onValueChange = { text = it; onChange(it) }, label = { Text(label) },
        singleLine = singleLine, minLines = minLines, modifier = modifier,
    )
}

@Composable
fun ScaleRow(label: String, value: Int?, onPick: (Int?) -> Unit) {
    Column {
        Muted(label)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            (1..5).forEach { n ->
                FilterChip(selected = value == n, onClick = { onPick(if (value == n) null else n) }, label = { Text("$n") })
            }
        }
    }
}

@Composable
fun Meter(fraction: Float, color: Color = MaterialTheme.colorScheme.primary, track: Color = MaterialTheme.colorScheme.surfaceVariant) {
    Box(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(6.dp)).background(track)) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(10.dp).clip(RoundedCornerShape(6.dp)).background(color))
    }
}
