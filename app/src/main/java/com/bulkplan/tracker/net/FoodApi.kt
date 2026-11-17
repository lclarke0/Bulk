package com.bulkplan.tracker.net

import com.bulkplan.tracker.data.Repo
import com.bulkplan.tracker.logic.Macros
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URLEncoder

/** A food with nutrition per 100 g and, when known, one serving's size. */
data class FoodResult(
    val name: String,
    val brand: String = "",
    val per100: Macros,
    val servingG: Double? = null,
    val servingLabel: String = "",
    val source: String,
)

object FoodApi {
    /** Open Food Facts: free, no key. Returns null when the barcode isn't in the database. */
    suspend fun byBarcode(code: String): FoodResult? {
        val url = "https://world.openfoodfacts.org/api/v2/product/${URLEncoder.encode(code, "UTF-8")}.json" +
            "?fields=product_name,brands,nutriments,serving_size,serving_quantity"
        val text = try { Http.request(url) } catch (e: HttpError) { if (e.code == 404) return null else throw e }
        val root = Repo.json.parseToJsonElement(text).jsonObject
        if (root["status"]?.jsonPrimitive?.intOrNull == 0) return null
        val p = root["product"] as? JsonObject ?: return null
        val n = p["nutriments"] as? JsonObject ?: JsonObject(emptyMap())
        fun num(k: String) = n[k]?.jsonPrimitive?.doubleOrNull
        val kcal = num("energy-kcal_100g") ?: num("energy_100g")?.let { it / 4.184 } ?: 0.0
        return FoodResult(
            name = p["product_name"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: "Barcode $code",
            brand = p["brands"]?.jsonPrimitive?.contentOrNull.orEmpty().split(",").first().trim(),
            per100 = Macros(kcal, num("proteins_100g") ?: 0.0, num("carbohydrates_100g") ?: 0.0, num("fat_100g") ?: 0.0),
            servingG = p["serving_quantity"]?.jsonPrimitive?.doubleOrNull?.takeIf { it > 0 },
            servingLabel = p["serving_size"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            source = "barcode",
        )
    }

    /**
     * USDA FoodData Central search. Works with the shared DEMO_KEY (low hourly limit);
     * a free personal key from api.data.gov raises that to 1,000 requests an hour.
     */
    suspend fun search(query: String, apiKey: String): List<FoodResult> {
        val key = apiKey.ifBlank { "DEMO_KEY" }
        val url = "https://api.nal.usda.gov/fdc/v1/foods/search?api_key=${URLEncoder.encode(key, "UTF-8")}" +
            "&query=${URLEncoder.encode(query, "UTF-8")}&pageSize=25&dataType=Foundation,SR%20Legacy,Branded"
        val text = try { Http.request(url) } catch (e: HttpError) {
            throw Exception(if (e.code == 429 || e.code == 403) "Food search limit reached. Add a free USDA key in Settings, or try again in an hour." else "Food search failed (HTTP ${e.code}).")
        }
        val foods = Repo.json.parseToJsonElement(text).jsonObject["foods"] as? JsonArray ?: return emptyList()
        return foods.mapNotNull { e ->
            val f = e as? JsonObject ?: return@mapNotNull null
            val nut = (f["foodNutrients"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
            fun byNum(vararg nums: String): Double? = nut.firstOrNull { o ->
                o["nutrientNumber"]?.jsonPrimitive?.contentOrNull in nums &&
                    (o["unitName"]?.jsonPrimitive?.contentOrNull?.uppercase() != "KJ")
            }?.get("value")?.jsonPrimitive?.doubleOrNull
            val kcal = byNum("208", "957", "958") ?: return@mapNotNull null
            val unit = f["servingSizeUnit"]?.jsonPrimitive?.contentOrNull?.lowercase()
            val size = f["servingSize"]?.jsonPrimitive?.doubleOrNull
            val servingG = if (size != null && (unit == "g" || unit == "grm" || unit == "ml" || unit == "mlt")) size else null
            FoodResult(
                name = f["description"]?.jsonPrimitive?.contentOrNull?.lowercase()?.replaceFirstChar { it.uppercase() } ?: "Food",
                brand = f["brandOwner"]?.jsonPrimitive?.contentOrNull ?: f["brandName"]?.jsonPrimitive?.contentOrNull ?: "",
                per100 = Macros(kcal, byNum("203") ?: 0.0, byNum("205") ?: 0.0, byNum("204") ?: 0.0),
                servingG = servingG,
                servingLabel = f["householdServingFullText"]?.jsonPrimitive?.contentOrNull ?: servingG?.let { "${it.toInt()} g" }.orEmpty(),
                source = "search",
            )
        }
    }
}
