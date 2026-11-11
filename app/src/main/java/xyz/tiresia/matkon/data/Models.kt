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
    /** Where it came from: "web" (a recipe page), "photo" (photos of a recipe), or none for a reel. */
    val source: String?,
    /** The steps and tips, for search (and their English copy). */
    val text: String?,
    val text_en: String?,
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
    val source: String?,
    /** A reel's recipe page, when the reel points to one. */
    val link: String?,
    val can_edit: Boolean?,
    val edited: Edited?,
    /** Its shared link, or null. */
    val share: String?,
) {
    val he get() = lang == "he"
    val groups get() = ingredient_groups.orEmpty()
    val stepList get() = steps.orEmpty().filter { !it.text.isNullOrBlank() }
}

data class Edited(val by: String?, val at: Double?)

data class PlanEntry(val id: String, val code: String, val day: String, val at: Double?)

data class ShopItem(val id: String, val item: String, val amount: String?, val code: String?, val done: Boolean, val at: Double?)

/** One time a recipe was made: with how much (½×–3×) and a note, when they were given. */
data class Cooked(val id: String, val code: String, val at: Double, val factor: Double? = null, val note: String? = null)

/** A person's own group of recipes. */
data class Coll(val id: String, val name: String?, val codes: List<String>?)

data class Memory(
    val saved: Map<String, Double>?,
    val ticks: Map<String, List<String>>?,
    val steps: Map<String, Int>?,
    val notes: Map<String, String>?,
    val ratings: Map<String, Int>?,
    val cooked: List<Cooked>?,
    val settings: Map<String, Any?>?,
    val plan: List<PlanEntry>?,
    val shop: List<ShopItem>?,
    val cooked_count: Map<String, Int>?,
    val cooked_last: Map<String, Double>?,
    val collections: List<Coll>? = null,
    /** What's in the kitchen. */
    val pantry: List<String>? = null,
    /** A person's own aisle for an item on the list: Amounts.key(item) → aisle. */
    val aisles: Map<String, String>? = null,
    /** The other accounts this one shares the week and the list with. */
    val household: List<String>? = null,
) {
    fun english() = settings?.get("english") == true

    fun nutrition() = settings?.get("nutrition") != false

    /** "" (as written), "metric" or "us". */
    fun units() = settings?.get("units") as? String ?: ""

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

data class AskResponse(val answer: String?)

data class ShareResponse(val ok: Boolean?, val url: String?)
