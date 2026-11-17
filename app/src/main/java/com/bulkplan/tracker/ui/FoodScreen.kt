@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.bulkplan.tracker.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
import java.time.format.DateTimeFormatter
import java.util.Locale

private sealed interface FoodDialog {
    data class Amount(val food: FoodResult) : FoodDialog
    data object Search : FoodDialog
    data object Describe : FoodDialog
    data class Photo(val uri: Uri) : FoodDialog
    data object Quick : FoodDialog
    data class Guesses(val list: List<Claude.FoodGuess>, val source: String) : FoodDialog
    data class Item(val entry: FoodEntry) : FoodDialog
}

fun mealIcon(meal: String): ImageVector = when (meal) {
    "Breakfast" -> Ico.Sunrise; "Lunch" -> Ico.Sun; "Dinner" -> Ico.Moon; else -> Ico.Cookie
}

private fun r(v: Double) = fmtN(Math.rint(v))

@Composable
fun FoodScreen() {
    val state by Repo.state.collectAsStateWithLifecycle()
    val logs by Repo.logs.collectAsStateWithLifecycle()
    var date by rememberSaveable { mutableStateOf(PlanLogic.today()) }
    var dialog by remember { mutableStateOf<FoodDialog?>(null) }
    var sheetFor by remember { mutableStateOf<String?>(null) }
    var meal by rememberSaveable { mutableStateOf("Snacks") }
    var busy by remember { mutableStateOf(false) }
    val collapsed = remember { mutableStateListOf<String>() }
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val toast = LocalToast.current
    val lg = logs[date] ?: DayLog(date)
    val tot = PlanLogic.foodTotals(lg)
    val plan = state.plan
    val pTarget = PlanLogic.proteinTarget(plan, logs)
    val fatTarget = plan.targets.fatG.toDouble()
    val carbTarget = ((plan.targets.calories - pTarget * 4 - plan.targets.fatG * 9) / 4.0).coerceAtLeast(0.0)
    val budgets = Actions.mealBudgets(plan, date)
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
                        val res = FoodApi.byBarcode(code)
                        if (res == null) toast("That barcode isn’t in the database. Try a label photo instead.") else dialog = FoodDialog.Amount(res)
                    } catch (e: Exception) { toast("Lookup failed: ${e.message}") }
                    busy = false
                }
            }
            .addOnFailureListener { e -> toast("Scanner unavailable: ${e.message}") }
    }

    fun photo() {
        val dir = File(ctx.cacheDir, "photos").apply { mkdirs() }
        val u = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", File(dir, "label.jpg"))
        photoUri = u
        takePhoto.launch(u)
    }

    LazyColumn(contentPadding = PaddingValues(16.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { date = PlanLogic.addD(date, -1) }) { Icon(Ico.Left, "Previous day", tint = C.Text) }
                Text(PlanLogic.date(date).format(DateTimeFormatter.ofPattern("EEE, MMM d, yyyy", Locale.US)),
                    modifier = Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 20.sp, color = C.Text)
                val canNext = date < PlanLogic.today()
                IconButton(onClick = { if (canNext) date = PlanLogic.addD(date, 1) }, enabled = canNext) {
                    Icon(Ico.Right, "Next day", tint = if (canNext) C.Text else C.Line)
                }
            }
        }
        item {
            Section {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Ring((tot.kcal / plan.targets.calories).toFloat(), 132.dp, color = C.Green, stroke = 12.dp) {
                        Text(r(tot.kcal), color = C.Text, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                        Text("/ ${fmtN(plan.targets.calories.toDouble())}", color = C.Text, fontSize = 15.sp)
                        Text("cal", color = C.BlueSoft, fontSize = 14.sp)
                    }
                    Spacer(Modifier.width(14.dp))
                    Box(Modifier.width(1.dp).height(120.dp).background(C.Line))
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        MacroMini(Ico.Arm, "Protein", C.BlueSoft, tot.protein, pTarget.toDouble())
                        MacroMini(Ico.Wheat, "Carbs", C.Yellow, tot.carbs, carbTarget)
                        MacroMini(Ico.Drop, "Fats", C.Red, tot.fat, fatTarget)
                    }
                }
                Muted("${r((plan.targets.calories - tot.kcal).coerceAtLeast(0.0))} cal and ${r((pTarget - tot.protein).coerceAtLeast(0.0))} g protein to go",
                    Modifier.fillMaxWidth().padding(top = 4.dp))
                if (busy) Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(20.dp), color = C.Blue, strokeWidth = 2.dp); Muted("  Working…")
                }
            }
        }
        Actions.MEALS.forEach { m ->
            val list = lg.foods.filter { it.meal == m }
            val kcal = list.sumOf { it.kcal }
            val budget = budgets[m]
            val isOpen = m !in collapsed
            item(key = m) {
                Column(Modifier.fillMaxWidth().padding(bottom = 12.dp).clip(RoundedCornerShape(18.dp)).background(C.Card)
                    .border(1.dp, C.Line, RoundedCornerShape(18.dp))) {
                    Row(Modifier.fillMaxWidth().background(C.CardHi).clickable { if (isOpen) collapsed.add(m) else collapsed.remove(m) }
                        .padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(mealIcon(m), null, tint = Color.Unspecified, modifier = Modifier.size(40.dp))
                        Spacer(Modifier.width(14.dp))
                        Text(m, style = H2, modifier = Modifier.weight(1f))
                        Text(r(kcal) + (budget?.let { " / $it" } ?: "") + " cal", fontSize = 17.sp, fontWeight = FontWeight.Bold,
                            color = if (budget != null && kcal >= budget) C.Green else C.Text)
                        Spacer(Modifier.width(10.dp))
                        Icon(if (isOpen) Ico.Up else Ico.Down, null, tint = C.BlueSoft, modifier = Modifier.size(22.dp))
                    }
                    if (isOpen) Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                        if (list.isEmpty()) Muted("Nothing logged yet.", Modifier.padding(vertical = 8.dp))
                        list.forEachIndexed { i, f ->
                            if (i > 0) HorizontalDivider(color = C.Line)
                            Row(Modifier.fillMaxWidth().clickable { dialog = FoodDialog.Item(f) }.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(f.name, fontSize = 17.sp, color = C.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Muted(f.detail.ifBlank { "P ${r(f.protein)} g · C ${r(f.carbs)} g · F ${r(f.fat)} g" })
                                }
                                Text("${r(f.kcal)} cal", fontSize = 17.sp, color = C.Text)
                                Spacer(Modifier.width(8.dp))
                                Icon(Ico.Right, null, tint = C.Muted, modifier = Modifier.size(20.dp))
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        AddButton("Add Meal", Modifier.padding(bottom = 10.dp)) { meal = m; sheetFor = m }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }

    sheetFor?.let { slot ->
        AddFoodSheet(slot, date, onDismiss = { sheetFor = null }) { action ->
            sheetFor = null
            when (action) {
                "scan" -> scan()
                "search" -> dialog = FoodDialog.Search
                "describe" -> dialog = FoodDialog.Describe
                "photo" -> photo()
                "quick" -> dialog = FoodDialog.Quick
            }
        }
    }

    when (val d = dialog) {
        null -> {}
        is FoodDialog.Amount -> AmountDialog(d.food, meal, onDismiss = { dialog = null }) { entry, save ->
            Actions.addFood(date, entry, save); dialog = null; toast("Added ${entry.name}")
        }
        FoodDialog.Search -> SearchDialog(state.settings.usdaKey, onDismiss = { dialog = null }) { dialog = FoodDialog.Amount(it) }
        FoodDialog.Describe -> DescribeDialog(onDismiss = { dialog = null }) { text ->
            dialog = null; busy = true
            scope.launch {
                try { dialog = FoodDialog.Guesses(Claude.estimateMeal(state.settings.claudeKey, state.settings.claudeModel, text), "claude") }
                catch (e: Exception) { toast(e.message ?: "Couldn’t estimate that") }
                busy = false
            }
        }
        is FoodDialog.Photo -> PhotoDialog(onDismiss = { dialog = null }) { servings, hint ->
            dialog = null; busy = true
            scope.launch {
                try {
                    val b64 = withContext(Dispatchers.IO) { encodePhoto(ctx, d.uri) }
                    dialog = FoodDialog.Guesses(Claude.readPhoto(state.settings.claudeKey, state.settings.claudeModel, b64, servings, hint), "label")
                } catch (e: Exception) { toast(e.message ?: "Couldn’t read that photo") }
                busy = false
            }
        }
        FoodDialog.Quick -> QuickDialog(meal, onDismiss = { dialog = null }) { entry, save -> Actions.addFood(date, entry, save); dialog = null }
        is FoodDialog.Guesses -> GuessDialog(d.list, d.source, meal, onDismiss = { dialog = null }) { entries, save ->
            entries.forEach { Actions.addFood(date, it, save) }; dialog = null; toast("Added ${entries.size} item${if (entries.size == 1) "" else "s"}")
        }
        is FoodDialog.Item -> FoodItemDialog(d.entry, onDismiss = { dialog = null },
            onMove = { Actions.moveFood(date, d.entry.id, it); dialog = null },
            onSave = { Actions.saveFood(d.entry); toast("Saved to your foods") },
            onDelete = { Actions.removeFood(date, d.entry.id); dialog = null })
    }
}

@Composable
private fun MacroMini(icon: ImageVector, label: String, color: Color, v: Double, target: Double) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = Color.Unspecified, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(6.dp))
            Text(label, color = color, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text(r(v), color = C.Text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text(" / ${r(target)} g", color = C.Text, fontSize = 13.sp)
        }
        Spacer(Modifier.height(6.dp))
        Meter(if (target > 0) (v / target).toFloat() else 0f, color = color)
    }
}

@Composable
private fun AddFoodSheet(meal: String, date: String, onDismiss: () -> Unit, onAction: (String) -> Unit) {
    val state by Repo.state.collectAsStateWithLifecycle()
    val logs by Repo.logs.collectAsStateWithLifecycle()
    val lg = logs[date] ?: DayLog(date)
    val toast = LocalToast.current
    val planMeals = PlanLogic.itemsFor(state.plan, date).filter { it.kind == "meal" && it.kcal != null && Actions.slotFor(it.time) == meal }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = C.Card) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 32.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(mealIcon(meal), null, tint = Color.Unspecified, modifier = Modifier.size(32.dp))
                Spacer(Modifier.width(12.dp))
                Text("Add to $meal", style = H2)
            }
            val opts = listOf(
                Triple("scan", "Scan barcode", Ico.Barcode), Triple("search", "Search foods", Ico.Search),
                Triple("describe", "Describe it", Ico.Sparkle), Triple("photo", "Label photo", Ico.Camera),
                Triple("quick", "Quick add", Ico.Plus),
            )
            opts.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { (k, label, icon) ->
                        Row(Modifier.weight(1f).height(56.dp).clip(RoundedCornerShape(14.dp)).background(C.Inner)
                            .border(1.dp, C.Line, RoundedCornerShape(14.dp)).clickable { onAction(k) }.padding(horizontal = 14.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Icon(icon, null, tint = C.BlueSoft, modifier = Modifier.size(24.dp))
                            Spacer(Modifier.width(10.dp))
                            Text(label, color = C.Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
            Muted("Describe it and Label photo use Claude to estimate calories and macros.")
            if (planMeals.isNotEmpty()) {
                Text("From your plan", style = H3)
                planMeals.forEach { pm ->
                    val on = lg.checks[pm.id] == true
                    Row(Modifier.fillMaxWidth().clickable { Actions.setChecked(date, pm, !on) }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(pm.title, color = C.Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                            Muted("${pm.detail.removeSuffix(".")} · ${pm.kcal} cal · ${pm.protein ?: 0} g")
                        }
                        Spacer(Modifier.width(10.dp))
                        CheckCircle(on) { Actions.setChecked(date, pm, !on) }
                    }
                }
            }
            val saved = state.savedFoods.sortedByDescending { it.uses }.take(12)
            if (saved.isNotEmpty()) {
                Text("Your foods", style = H3)
                saved.forEach { sf ->
                    Row(Modifier.fillMaxWidth().clickable {
                        Actions.addFood(date, FoodEntry(Actions.newId(), sf.name, sf.kcal, sf.protein, sf.carbs, sf.fat, meal, "saved", Instant.now().toString()))
                        toast("Added ${sf.name}")
                    }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Ico.Bookmark, null, tint = C.Muted, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(sf.name, color = C.Text, fontSize = 16.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${r(sf.kcal)} cal", color = C.Muted, fontSize = 15.sp)
                        Spacer(Modifier.width(8.dp))
                        Icon(Ico.Plus, "Add", tint = C.BlueSoft, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun MealPicker(meal: String, onPick: (String) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) { Actions.MEALS.forEach { m -> Chip(m, meal == m) { onPick(m) } } }
}

@Composable
private fun SaveRow(save: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(save, onChange, colors = CheckboxDefaults.colors(checkedColor = C.Blue, uncheckedColor = C.Muted))
        Text("Save to my foods", color = C.Text)
    }
}

@Composable
private fun AmountDialog(food: FoodResult, initialMeal: String, onDismiss: () -> Unit, onAdd: (FoodEntry, Boolean) -> Unit) {
    val hasServing = food.servingG != null
    var byServing by remember { mutableStateOf(hasServing) }
    var amount by remember { mutableStateOf(if (hasServing) "1" else "100") }
    var meal by remember { mutableStateOf(initialMeal) }
    var save by remember { mutableStateOf(false) }
    val qty = amount.toDoubleOrNull() ?: 0.0
    val grams = if (byServing) qty * (food.servingG ?: 100.0) else qty
    val m: Macros = food.per100 * (grams / 100.0)
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = C.Card,
        title = { Text(food.name, color = C.Text) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (food.brand.isNotBlank()) Muted(food.brand)
                if (hasServing) Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Chip("Servings" + if (food.servingLabel.isNotBlank()) " (${food.servingLabel})" else "", byServing) { byServing = true; amount = "1" }
                    Chip("Grams", !byServing) { byServing = false; amount = fmtN(food.servingG).replace(",", "") }
                }
                Field(amount, { amount = it }, if (byServing) "Servings" else "Grams", number = true)
                Text("${r(m.kcal)} cal · P ${r(m.protein)} g · C ${r(m.carbs)} g · F ${r(m.fat)} g", color = C.Text, fontWeight = FontWeight.Bold)
                MealPicker(meal) { meal = it }
                SaveRow(save) { save = it }
            }
        },
        confirmButton = {
            Button(enabled = qty > 0, onClick = {
                val label = if (byServing) "${fmtN(qty)} serving${if (qty == 1.0) "" else "s"}" + (if (food.servingLabel.isNotBlank()) " (${food.servingLabel})" else "") else "${fmtN(qty)} g"
                onAdd(FoodEntry(Actions.newId(), food.name, m.kcal, m.protein, m.carbs, m.fat, meal, food.source, Instant.now().toString(),
                    detail = listOf(label, food.brand).filter { it.isNotBlank() }.joinToString(" · ")), save)
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
        onDismissRequest = onDismiss, containerColor = C.Card,
        title = { Text("Search foods", color = C.Text) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Field(q, { q = it }, "e.g. 90/10 ground beef", Modifier.fillMaxWidth())
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
                    results.forEach { res ->
                        Column(Modifier.fillMaxWidth().clickable { onPick(res) }.padding(vertical = 8.dp)) {
                            Text(res.name, color = C.Text, fontWeight = FontWeight.SemiBold, maxLines = 2)
                            Muted(listOf(res.brand, "${r(res.per100.kcal)} cal · ${fmtN(res.per100.protein)} g protein per 100 g").filter { it.isNotBlank() }.joinToString(" · "))
                        }
                        HorizontalDivider(color = C.Line)
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
        onDismissRequest = onDismiss, containerColor = C.Card,
        title = { Text("Describe what you ate", color = C.Text) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Field(text, { text = it }, "e.g. Chipotle chicken bowl, double rice, no beans", Modifier.fillMaxWidth(), singleLine = false, minLines = 3)
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
        onDismissRequest = onDismiss, containerColor = C.Card,
        title = { Text("Read the photo", color = C.Text) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Field(servings, { servings = it }, "Servings you ate (for labels)", number = true)
                Field(hint, { hint = it }, "What is it? (optional)")
            }
        },
        confirmButton = { Button(onClick = { onGo(servings.toDoubleOrNull() ?: 1.0, hint) }) { Text("Read with Claude") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun QuickDialog(initialMeal: String, onDismiss: () -> Unit, onAdd: (FoodEntry, Boolean) -> Unit) {
    var name by remember { mutableStateOf("") }
    var portion by remember { mutableStateOf("") }
    var kcal by remember { mutableStateOf("") }
    var p by remember { mutableStateOf("") }
    var c by remember { mutableStateOf("") }
    var f by remember { mutableStateOf("") }
    var meal by remember { mutableStateOf(initialMeal) }
    var save by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = C.Card,
        title = { Text("Quick add", color = C.Text) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Field(name, { name = it }, "Name")
                Field(portion, { portion = it }, "Portion (e.g. 1 bowl)")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Field(kcal, { kcal = it }, "Calories", Modifier.weight(1f), number = true)
                    Field(p, { p = it }, "Protein g", Modifier.weight(1f), number = true)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Field(c, { c = it }, "Carbs g", Modifier.weight(1f), number = true)
                    Field(f, { f = it }, "Fat g", Modifier.weight(1f), number = true)
                }
                MealPicker(meal) { meal = it }
                SaveRow(save) { save = it }
            }
        },
        confirmButton = {
            Button(enabled = name.isNotBlank() && kcal.toDoubleOrNull() != null, onClick = {
                onAdd(FoodEntry(Actions.newId(), name.trim(), kcal.toDoubleOrNull() ?: 0.0, p.toDoubleOrNull() ?: 0.0, c.toDoubleOrNull() ?: 0.0,
                    f.toDoubleOrNull() ?: 0.0, meal, "quick", Instant.now().toString(), detail = portion.trim()), save)
            }) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun GuessDialog(list: List<Claude.FoodGuess>, source: String, initialMeal: String, onDismiss: () -> Unit, onAdd: (List<FoodEntry>, Boolean) -> Unit) {
    val picked = remember { mutableStateListOf<Int>().apply { addAll(list.indices) } }
    var meal by remember { mutableStateOf(initialMeal) }
    var save by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = C.Card,
        title = { Text("Claude’s estimate", color = C.Text) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                list.forEachIndexed { i, g ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(i in picked, { if (it) picked.add(i) else picked.remove(i) }, colors = CheckboxDefaults.colors(checkedColor = C.Blue, uncheckedColor = C.Muted))
                        Column {
                            Text(g.name, color = C.Text, fontWeight = FontWeight.SemiBold)
                            Muted("${r(g.macros.kcal)} cal · P ${r(g.macros.protein)} · C ${r(g.macros.carbs)} · F ${r(g.macros.fat)}")
                        }
                    }
                }
                list.firstOrNull()?.note?.takeIf { it.isNotBlank() }?.let { Muted(it) }
                MealPicker(meal) { meal = it }
                SaveRow(save) { save = it }
            }
        },
        confirmButton = {
            Button(enabled = picked.isNotEmpty(), onClick = {
                onAdd(picked.sorted().map { i ->
                    val g = list[i]
                    FoodEntry(Actions.newId(), g.name, g.macros.kcal, g.macros.protein, g.macros.carbs, g.macros.fat, meal, source, Instant.now().toString(),
                        detail = if (source == "label") "From a label photo" else "Estimated by Claude")
                }, save)
            }) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun FoodItemDialog(f: FoodEntry, onDismiss: () -> Unit, onMove: (String) -> Unit, onSave: () -> Unit, onDelete: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = C.Card,
        title = { Text(f.name, color = C.Text) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (f.detail.isNotBlank()) Muted(f.detail)
                Text("${r(f.kcal)} cal", style = Big)
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("Protein ${r(f.protein)} g", color = C.BlueSoft, fontWeight = FontWeight.SemiBold)
                    Text("Carbs ${r(f.carbs)} g", color = C.Yellow, fontWeight = FontWeight.SemiBold)
                    Text("Fat ${r(f.fat)} g", color = C.Red, fontWeight = FontWeight.SemiBold)
                }
                Muted("Move to")
                MealPicker(f.meal, onMove)
                PillButton("Save to my foods", Ico.Bookmark, onClick = onSave)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
        dismissButton = { TextButton(onClick = onDelete) { Text("Remove", color = C.Red) } },
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
