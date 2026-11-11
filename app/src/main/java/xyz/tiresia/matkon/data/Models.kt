package xyz.tiresia.matkon.data

// Matkon's JSON, as its server sends it. Gson fills these by field name, so every field may be null.

data class Times(val prep: String?, val cook: String?, val total: String?)

data class Ingredient(val amount: String?, val item: String?, val note: String?)

data class IngredientGroup(val name: String?, val items: List<Ingredient>?)

data class Step(val text: String?, val timers: List<Int>?)

data class IndexEntry(
    val code: String,
    val title: String?,
    val summary: String?,
    val kind: String?,
    val lang: String?,
    val user: String?,
    val tags: List<String>?,
    val cuisine: String?,
    val complete: Boolean?,
    val is_recipe: Boolean?,
    val found: Double?,
    val posted: Double?,
    val name: String?,
    val photo: String?,
    val time: Times?,
    val servings: String?,
    val main_ingredients: List<String>?,
    val added_by: String?,
    val items: List<String>?,
    val title_en: String?,
    val items_en: List<String>?,
)

data class Index(val recipes: List<IndexEntry>?, val waiting: Int?)

data class Recipe(
    val code: String,
    val title: String?,
    val summary: String?,
    val kind: String?,
    val cuisine: String?,
    val tags: List<String>?,
    val servings: String?,
    val time: Times?,
    val oven: String?,
    val ingredient_groups: List<IngredientGroup>?,
    val steps: List<Step>?,
    val tips: List<String>?,
    val nutrition: Map<String, Any?>?,
    val complete: Boolean?,
    val missing: List<String>?,
    val is_recipe: Boolean?,
    val lang: String?,
    val langs: List<String>?,
    val original_lang: String?,
    val user: String?,
    val name: String?,
    val reel: String?,
    val found: Double?,
    val posted: Double?,
    val added_by: String?,
    val photo: String?,
    val video: String?,
) {
    val he get() = lang == "he"
    val groups get() = ingredient_groups.orEmpty()
    val stepList get() = steps.orEmpty().filter { !it.text.isNullOrBlank() }
}

data class PlanEntry(val id: String, val code: String, val day: String, val at: Double?)

data class ShopItem(val id: String, val item: String, val amount: String?, val code: String?, val done: Boolean, val at: Double?)

data class Cooked(val id: String, val code: String, val at: Double)

data class Memory(
    val saved: Map<String, Double>?,
    val ticks: Map<String, List<String>>?,
    val steps: Map<String, Int>?,
    val notes: Map<String, String>?,
    val ratings: Map<String, Int>?,
    val cooked: List<Cooked>?,
    val settings: Map<String, Boolean>?,
    val plan: List<PlanEntry>?,
    val shop: List<ShopItem>?,
    val cooked_count: Map<String, Int>?,
    val cooked_last: Map<String, Double>?,
) {
    fun english() = settings?.get("english") == true
    fun nutrition() = settings?.get("nutrition") != false

    companion object {
        val EMPTY = Memory(null, null, null, null, null, null, null, null, null, null, null)
    }
}

data class Account(val id: String?, val name: String?, val admin: Boolean?, val email: String?)

data class Me(val account: Account?, val owner: String?, val memory: Memory?, val names: Map<String, String>?)

data class MemoryResponse(val memory: Memory?)

data class Suggestion(val code: String, val why: String?, val about: Any?)

data class SuggestResponse(val suggest: List<Suggestion>?)

data class AddResult(val ok: Boolean?, val code: String?, val new: Boolean?, val done: Boolean?)
