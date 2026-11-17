@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.bulkplan.tracker.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bulkplan.tracker.LocalToast
import com.bulkplan.tracker.data.DayLog
import com.bulkplan.tracker.data.FoodEntry
import com.bulkplan.tracker.data.Repo
import com.bulkplan.tracker.logic.Actions
import com.bulkplan.tracker.logic.Macros
import com.bulkplan.tracker.logic.PlanLogic
import com.bulkplan.tracker.logic.PlanLogic.fmtN
import com.bulkplan.tracker.net.Claude
import com.bulkplan.tracker.net.FoodApi
import com.bulkplan.tracker.net.FoodResult
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.Instant
import java.time.LocalTime

private sealed interface FoodDialog {
    data class Amount(val food: FoodResult) : FoodDialog
    data object Search : FoodDialog
    data object Describe : FoodDialog
    data class Photo(val uri: Uri) : FoodDialog
    data object Quick : FoodDialog
    data class Guesses(val list: List<Claude.FoodGuess>, val source: String) : FoodDialog
}

private fun defaultMeal(): String {
    val h = LocalTime.now().hour
    return when { h < 10 -> "Breakfast"; h in 12..14 -> "Lunch"; h in 19..21 -> "Dinner"; else -> "Snacks" }
}

@Composable
fun FoodScreen() {
    val state by Repo.state.collectAsStateWithLifecycle()
    val logs by Repo.logs.collectAsStateWithLifecycle()
    var date by remember { mutableStateOf(PlanLogic.today()) }
    var dialog by remember { mutableStateOf<FoodDialog?>(null) }
    var busy by remember { mutableStateOf(false) }
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val toast = LocalToast.current
    val lg = logs[date] ?: DayLog(date)
    val tot = PlanLogic.foodTotals(lg)
    val plan = state.plan
    val pTarget = PlanLogic.proteinTarget(plan, logs)
    var photoUri by remember { mutableStateOf<Uri?>(null) }

    val takePhoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val u = photoUri
        if (ok && u != null) dialog = FoodDialog.Photo(u)
    }

    fun scan() {
        val opts = GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_EAN_13, Barcode.FORMAT_UPC_A, Barcode.FORMAT_EAN_8, Barcode.FORMAT_UPC_E)
            .build()
        GmsBarcodeScanning.getClient(ctx, opts).startScan()
            .addOnSuccessListener { b ->
                val code = b.rawValue ?: return@addOnSuccessListener
                busy = true
                scope.launch {
                    try {
                        val r = FoodApi.byBarcode(code)
                        if (r == null) toast("That barcode isn’t in the database. Try a label photo instead.") else dialog = FoodDialog.Amount(r)
                    } catch (e: Exception) { toast("Lookup failed: ${e.message}") }
                    busy = false
                }
            }
            .addOnFailureListener { e -> toast("Scanner unavailable: ${e.message}") }
    }

    fun photo() {
        val dir = File(ctx.cacheDir, "photos").apply { mkdirs() }
        val f = File(dir, "label.jpg")
        val u = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)
        photoUri = u
        takePhoto.launch(u)
    }

    LazyColumn(contentPadding = PaddingValues(16.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { date = PlanLogic.addD(date, -1) }) { Icon(Icons.Filled.KeyboardArrowLeft, "Previous day") }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(PlanLogic.fmtD(date), style = H2)
                    Muted(if (date == PlanLogic.today()) "Today" else "${-PlanLogic.diffD(date, PlanLogic.today())} days ago")
                }
                IconButton(onClick = { if (date < PlanLogic.today()) date = PlanLogic.addD(date, 1) }, enabled = date < PlanLogic.today()) {
                    Icon(Icons.Filled.KeyboardArrowRight, "Next day")
                }
            }
        }
        item {
            Section {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(fmtN(tot.kcal), style = Big)
                    Text(" / ${fmtN(plan.targets.calories.toDouble())} kcal", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 4.dp))
                }
                Meter((tot.kcal / plan.targets.calories).toFloat())
                MacroLine("Protein", tot.protein, pTarget.toDouble())
                MacroLine("Fat", tot.fat, plan.targets.fatG.toDouble())
                val carbTarget = ((plan.targets.calories - pTarget * 4 - plan.targets.fatG * 9) / 4.0).coerceAtLeast(0.0)
                MacroLine("Carbs", tot.carbs, carbTarget)
                Muted("${fmtN((plan.targets.calories - tot.kcal).coerceAtLeast(0.0))} kcal and ${fmtN((pTarget - tot.protein).coerceAtLeast(0.0))} g protein left")
            }
        }
        item {
            Section("Add food") {
                if (busy) Row(verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(Modifier.height(24.dp)); Muted("  Working…") }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { scan() }) { Text("Scan barcode") }
                    OutlinedButton(onClick = { dialog = FoodDialog.Search }) { Text("Search") }
                    OutlinedButton(onClick = { dialog = FoodDialog.Describe }) { Text("Describe it") }
                    OutlinedButton(onClick = { photo() }) { Text("Label photo") }
                    OutlinedButton(onClick = { dialog = FoodDialog.Quick }) { Text("Quick add") }
                }
                Muted("Describe it and Label photo use Claude to estimate calories and macros.")
                val planMeals = PlanLogic.itemsFor(plan, date).filter { it.kind == "meal" && it.kcal != null }
                if (planMeals.isNotEmpty()) {
                    Text("Plan meals", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp))
                    planMeals.forEach { m ->
                        val on = lg.checks[m.id] == true
                        Row(Modifier.fillMaxWidth().clickable { Actions.setChecked(date, m, !on) }, verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(m.title, fontWeight = FontWeight.SemiBold)
                                Muted("${m.kcal} kcal · ${m.protein ?: 0} g protein")
                            }
                            Checkbox(checked = on, onCheckedChange = { Actions.setChecked(date, m, it) })
                        }
                    }
                }
                val saved = state.savedFoods.sortedByDescending { it.uses }.take(10)
                if (saved.isNotEmpty()) {
                    Text("Saved foods", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        saved.forEach { sf ->
                            FilterChip(selected = false, onClick = {
                                Actions.addFood(date, FoodEntry(Actions.newId(), sf.name, sf.kcal, sf.protein, sf.carbs, sf.fat, defaultMeal(), "saved", Instant.now().toString()))
                                toast("Added ${sf.name}")
                            }, label = { Text("${sf.name} · ${fmtN(sf.kcal)}", maxLines = 1) })
                        }
                    }
                }
            }
        }
        Actions.MEALS.forEach { meal ->
            val list = lg.foods.filter { it.meal == meal }
            item {
                Section(meal, action = { if (list.isNotEmpty()) Muted("${fmtN(list.sumOf { it.kcal })} kcal · ${fmtN(list.sumOf { it.protein })} g") }) {
                    if (list.isEmpty()) Muted("Nothing logged.")
                    list.forEach { f ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(f.name, fontSize = 15.sp, maxLines = 2)
                                Muted("${fmtN(f.kcal)} kcal · P ${fmtN(f.protein)} · C ${fmtN(f.carbs)} · F ${fmtN(f.fat)}")
                            }
                            IconButton(onClick = { Actions.removeFood(date, f.id) }) { Icon(Icons.Filled.Close, "Remove ${f.name}") }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }

    when (val d = dialog) {
        null -> {}
        is FoodDialog.Amount -> AmountDialog(d.food, onDismiss = { dialog = null }) { entry, save ->
            Actions.addFood(date, entry, save); dialog = null; toast("Added ${entry.name}")
        }
        FoodDialog.Search -> SearchDialog(state.settings.usdaKey, onDismiss = { dialog = null }) { dialog = FoodDialog.Amount(it) }
        FoodDialog.Describe -> DescribeDialog(onDismiss = { dialog = null }) { text ->
            dialog = null; busy = true
            scope.launch {
                try { dialog = FoodDialog.Guesses(Claude.estimateMeal(state.settings.claudeKey, state.settings.claudeModel, text), "claude") }
                catch (e: Exception) { toast(e.message ?: "Claude couldn’t estimate that") }
                busy = false
            }
        }
        is FoodDialog.Photo -> PhotoDialog(onDismiss = { dialog = null }) { servings, hint ->
            dialog = null; busy = true
            scope.launch {
                try {
                    val b64 = withContext(Dispatchers.IO) { encodePhoto(ctx, d.uri) }
                    dialog = FoodDialog.Guesses(Claude.readPhoto(state.settings.claudeKey, state.settings.claudeModel, b64, servings, hint), "label")
                } catch (e: Exception) { toast(e.message ?: "Claude couldn’t read that photo") }
                busy = false
            }
        }
        FoodDialog.Quick -> QuickDialog(onDismiss = { dialog = null }) { entry, save -> Actions.addFood(date, entry, save); dialog = null }
        is FoodDialog.Guesses -> GuessDialog(d.list, d.source, onDismiss = { dialog = null }) { entries, save ->
            entries.forEach { Actions.addFood(date, it, save) }; dialog = null; toast("Added ${entries.size} item${if (entries.size == 1) "" else "s"}")
        }
    }
}

@Composable
private fun MacroLine(label: String, v: Double, target: Double) {
    Column {
        Row { Text(label, modifier = Modifier.weight(1f), fontSize = 14.sp); Text("${fmtN(v)} / ${fmtN(target)} g", fontSize = 14.sp) }
        Meter(if (target > 0) (v / target).toFloat() else 0f, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun MealPicker(meal: String, onPick: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Actions.MEALS.forEach { m -> FilterChip(selected = meal == m, onClick = { onPick(m) }, label = { Text(m) }) }
    }
}

@Composable
private fun AmountDialog(food: FoodResult, onDismiss: () -> Unit, onAdd: (FoodEntry, Boolean) -> Unit) {
    val hasServing = food.servingG != null
    var byServing by remember { mutableStateOf(hasServing) }
    var amount by remember { mutableStateOf(if (hasServing) "1" else "100") }
    var meal by remember { mutableStateOf(defaultMeal()) }
    var save by remember { mutableStateOf(false) }
    val qty = amount.toDoubleOrNull() ?: 0.0
    val grams = if (byServing) qty * (food.servingG ?: 100.0) else qty
    val m: Macros = food.per100 * (grams / 100.0)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(food.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (food.brand.isNotBlank()) Muted(food.brand)
                if (hasServing) Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = byServing, onClick = { byServing = true; amount = "1" }, label = { Text("Servings" + if (food.servingLabel.isNotBlank()) " (${food.servingLabel})" else "") })
                    FilterChip(selected = !byServing, onClick = { byServing = false; amount = fmtN(food.servingG).replace(",", "") }, label = { Text("Grams") })
                }
                OutlinedTextField(amount, { amount = it }, label = { Text(if (byServing) "Servings" else "Grams") }, singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal))
                Text("${fmtN(m.kcal)} kcal · P ${fmtN(m.protein)} g · C ${fmtN(m.carbs)} g · F ${fmtN(m.fat)} g", fontWeight = FontWeight.Bold)
                MealPicker(meal) { meal = it }
                Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(save, { save = it }); Text("Save to my foods") }
            }
        },
        confirmButton = {
            Button(enabled = qty > 0, onClick = {
                val label = if (byServing) "${fmtN(qty)} serving${if (qty == 1.0) "" else "s"}" else "${fmtN(qty)} g"
                onAdd(FoodEntry(Actions.newId(), "${food.name} ($label)", m.kcal, m.protein, m.carbs, m.fat, meal, food.source, Instant.now().toString()), save)
            }) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun SearchDialog(usdaKey: String, onDismiss: () -> Unit, onPick: (FoodResult) -> Unit) {
    var q by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var err by remember { mutableStateOf<String?>(null) }
    val results = remember { mutableStateListOf<FoodResult>() }
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Search foods") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(q, { q = it }, label = { Text("e.g. 90/10 ground beef") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Button(enabled = q.isNotBlank() && !busy, onClick = {
                    busy = true; err = null
                    scope.launch {
                        try { results.clear(); results.addAll(FoodApi.search(q, usdaKey)); if (results.isEmpty()) err = "No matches." }
                        catch (e: Exception) { err = e.message }
                        busy = false
                    }
                }) { Text(if (busy) "Searching…" else "Search") }
                err?.let { Muted(it) }
                Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                    results.forEach { r ->
                        Column(Modifier.fillMaxWidth().clickable { onPick(r) }.padding(vertical = 8.dp)) {
                            Text(r.name, fontWeight = FontWeight.SemiBold, maxLines = 2)
                            Muted(listOf(r.brand, "${fmtN(r.per100.kcal)} kcal · ${fmtN(r.per100.protein)} g protein per 100 g").filter { it.isNotBlank() }.joinToString(" · "))
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    }
                }
                Muted("Data: USDA FoodData Central.")
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
private fun DescribeDialog(onDismiss: () -> Unit, onGo: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Describe what you ate") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(text, { text = it }, minLines = 3, modifier = Modifier.fillMaxWidth(),
                    label = { Text("e.g. Chipotle chicken burrito bowl, double rice, no beans") })
                Muted("Claude estimates calories and macros. Include portions and restaurant names when you can.")
            }
        },
        confirmButton = { Button(enabled = text.isNotBlank(), onClick = { onGo(text) }) { Text("Estimate") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun PhotoDialog(onDismiss: () -> Unit, onGo: (Double, String) -> Unit) {
    var servings by remember { mutableStateOf("1") }
    var hint by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Read the photo") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(servings, { servings = it }, label = { Text("Servings you ate (for labels)") }, singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal))
                OutlinedTextField(hint, { hint = it }, label = { Text("What is it? (optional)") }, singleLine = true)
            }
        },
        confirmButton = { Button(onClick = { onGo(servings.toDoubleOrNull() ?: 1.0, hint) }) { Text("Read with Claude") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun QuickDialog(onDismiss: () -> Unit, onAdd: (FoodEntry, Boolean) -> Unit) {
    var name by remember { mutableStateOf("") }
    var kcal by remember { mutableStateOf("") }
    var p by remember { mutableStateOf("") }
    var c by remember { mutableStateOf("") }
    var f by remember { mutableStateOf("") }
    var meal by remember { mutableStateOf(defaultMeal()) }
    var save by remember { mutableStateOf(false) }
    val num = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Quick add") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(kcal, { kcal = it }, label = { Text("kcal") }, singleLine = true, keyboardOptions = num, modifier = Modifier.weight(1f))
                    OutlinedTextField(p, { p = it }, label = { Text("Protein") }, singleLine = true, keyboardOptions = num, modifier = Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(c, { c = it }, label = { Text("Carbs") }, singleLine = true, keyboardOptions = num, modifier = Modifier.weight(1f))
                    OutlinedTextField(f, { f = it }, label = { Text("Fat") }, singleLine = true, keyboardOptions = num, modifier = Modifier.weight(1f))
                }
                MealPicker(meal) { meal = it }
                Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(save, { save = it }); Text("Save to my foods") }
            }
        },
        confirmButton = {
            Button(enabled = name.isNotBlank() && kcal.toDoubleOrNull() != null, onClick = {
                onAdd(FoodEntry(Actions.newId(), name.trim(), kcal.toDoubleOrNull() ?: 0.0, p.toDoubleOrNull() ?: 0.0, c.toDoubleOrNull() ?: 0.0,
                    f.toDoubleOrNull() ?: 0.0, meal, "quick", Instant.now().toString()), save)
            }) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun GuessDialog(list: List<Claude.FoodGuess>, source: String, onDismiss: () -> Unit, onAdd: (List<FoodEntry>, Boolean) -> Unit) {
    val picked = remember { mutableStateListOf<Int>().apply { addAll(list.indices) } }
    var meal by remember { mutableStateOf(defaultMeal()) }
    var save by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Claude’s estimate") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                list.forEachIndexed { i, g ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(i in picked, { if (it) picked.add(i) else picked.remove(i) })
                        Column {
                            Text(g.name, fontWeight = FontWeight.SemiBold)
                            Muted("${fmtN(g.macros.kcal)} kcal · P ${fmtN(g.macros.protein)} · C ${fmtN(g.macros.carbs)} · F ${fmtN(g.macros.fat)}")
                        }
                    }
                }
                list.firstOrNull()?.note?.takeIf { it.isNotBlank() }?.let { Muted(it) }
                MealPicker(meal) { meal = it }
                Row(verticalAlignment = Alignment.CenterVertically) { Checkbox(save, { save = it }); Text("Save to my foods") }
            }
        },
        confirmButton = {
            Button(enabled = picked.isNotEmpty(), onClick = {
                onAdd(picked.sorted().map { i ->
                    val g = list[i]
                    FoodEntry(Actions.newId(), g.name, g.macros.kcal, g.macros.protein, g.macros.carbs, g.macros.fat, meal, source, Instant.now().toString())
                }, save)
            }) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Shrinks a camera photo to at most 1568 px on its long side and returns it as base64 JPEG. */
private fun encodePhoto(ctx: Context, uri: Uri): String {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    ctx.contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, bounds) }
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 3000) sample *= 2
    val bmp = ctx.contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
        ?: throw Exception("Couldn’t open the photo")
    val scale = 1568f / maxOf(bmp.width, bmp.height)
    val out = if (scale < 1f) Bitmap.createScaledBitmap(bmp, (bmp.width * scale).toInt(), (bmp.height * scale).toInt(), true) else bmp
    val bytes = ByteArrayOutputStream().use { out.compress(Bitmap.CompressFormat.JPEG, 85, it); it.toByteArray() }
    return Base64.encodeToString(bytes, Base64.NO_WRAP)
}
