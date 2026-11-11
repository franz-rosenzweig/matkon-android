package xyz.tiresia.matkon.ui

import xyz.tiresia.matkon.data.Amounts
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Labels on a recipe and while cooking follow the recipe's language, as on the web. */
data class Labels(
    val ing: String,
    val steps: String,
    val tips: String,
    val nut: String,
    val miss: String,
    val serves: String,
    val prep: String,
    val cook: String,
    val total: String,
    val oven: String,
    val start: String,
    val cont: (Int) -> String,
    val step: (Int, Int) -> String,
    val next: String,
    val back: String,
    val done: String,
    val finished: String,
    val uses: String,
    val timer: (Int) -> String,
    val noAmt: String,
    val close: String,
    val rating: String,
    val notes: String,
    val madeIt: String,
    val made: (Int, String) -> String,
    val posted: (String) -> String,
    val added: (String, String?) -> String,
    val insta: String,
    val amounts: String,
    val plan: String,
    val list: String,
    val onList: String,
    val watch: String,
    val ingredients: String,
    val again: String,
    val toRecipe: String,
    val calories: String,
    val protein: String,
    val carbs: String,
    val fat: String,
    val makes: String,
    val page: String,
    val more: String,
    val edit: String,
    val print: String,
    val share: String,
    val collections: String,
    val ask: String,
    val askPh: String,
    val asking: String,
    val again2: String,
    val edited: (String) -> String,
    val noteLbl: String,
    val title: String,
    val summary: String,
    val servings: String,
    val group: String,
    val amount: String,
    val item: String,
    val note: String,
    val addIng: String,
    val addGroup: String,
    val addStep: String,
    val save: String,
    val cancel: String,
    val revert: String,
)

val EN =
    Labels(
        ing = "Ingredients", steps = "Steps", tips = "Tips", nut = "Nutrition", miss = "Not in the reel", serves = "Serves",
        prep = "Prep", cook = "Cook", total = "Total", oven = "Oven", start = "Start cooking",
        cont = { "Continue · step $it" }, step = { k, n -> "Step $k of $n" }, next = "Next", back = "Back", done = "Done",
        finished = "Made it", uses = "Uses", timer = { "Timer ${Amounts.minutes(it)}" }, noAmt = "no amount", close = "Close",
        rating = "Rating", notes = "Notes", madeIt = "I made it",
        made = { n, d -> "Made ${if (n == 1) "once" else "$n times"} · $d" },
        posted = { "Posted $it" }, added = { d, who -> "Added $d" + (if (who != null) " by $who" else "") }, insta = "Instagram",
        amounts = "Amounts", plan = "Plan", list = "Add to list", onList = "On the list", watch = "Watch the reel",
        ingredients = "Ingredients", again = "Start again", toRecipe = "Back to the recipe",
        calories = "Calories", protein = "Protein", carbs = "Carbs", fat = "Fat", makes = "Makes",
        page = "Recipe page", more = "More", edit = "Fix this recipe", print = "Print", share = "Share a link", collections = "Collections",
        ask = "Ask", askPh = "Ask about this recipe", asking = "…", again2 = "Again, like last time", edited = { "Edited $it" },
        noteLbl = "Note", title = "Title", summary = "Summary", servings = "Serves", group = "Group", amount = "Amount",
        item = "Ingredient", note = "Note", addIng = "Ingredient", addGroup = "Group", addStep = "Step", save = "Save",
        cancel = "Cancel", revert = "Undo my changes",
    )

val HE =
    Labels(
        ing = "מצרכים", steps = "אופן ההכנה", tips = "טיפים", nut = "ערכים תזונתיים", miss = "חסר בסרטון", serves = "מנות",
        prep = "הכנה", cook = "בישול", total = "סה״כ", oven = "תנור", start = "מתחילים לבשל",
        cont = { "ממשיכים · שלב $it" }, step = { k, n -> "שלב $k מתוך $n" }, next = "הבא", back = "הקודם", done = "סיימתי",
        finished = "בתיאבון", uses = "צריך", timer = { "טיימר $it דק׳" }, noAmt = "בלי כמות", close = "סגירה",
        rating = "דירוג", notes = "הערות", madeIt = "הכנתי",
        made = { n, d -> "הוכן ${if (n == 1) "פעם אחת" else "$n פעמים"} · $d" },
        posted = { "פורסם $it" }, added = { d, who -> "נוסף $d" + (if (who != null) " על ידי $who" else "") }, insta = "אינסטגרם",
        amounts = "כמויות", plan = "לתכנן", list = "לרשימת הקניות", onList = "ברשימה", watch = "לצפייה בסרטון",
        ingredients = "מצרכים", again = "מההתחלה", toRecipe = "חזרה למתכון",
        calories = "קלוריות", protein = "חלבון", carbs = "פחמימות", fat = "שומן", makes = "כמות",
        page = "דף המתכון", more = "עוד", edit = "תיקון המתכון", print = "הדפסה", share = "קישור לשיתוף", collections = "אוספים",
        ask = "שאלה", askPh = "שאלה על המתכון", asking = "…", again2 = "שוב, כמו בפעם הקודמת", edited = { "נערך $it" },
        noteLbl = "הערה", title = "שם", summary = "תקציר", servings = "מנות", group = "קבוצה", amount = "כמות",
        item = "מצרך", note = "הערה", addIng = "מצרך", addGroup = "קבוצה", addStep = "שלב", save = "שמירה",
        cancel = "ביטול", revert = "ביטול השינויים שלי",
    )

fun labelsFor(lang: String?) = if (lang == "he") HE else EN

/** "Serves 4" for people, "Makes 16 cookies" for things, as the web decides it. */
fun servesOrMakes(
    servings: String,
    l: Labels,
) = if (Regex("""^\d+([–-]\d+)?( |$)(as\b|people|servings?\b|$)""").containsMatchIn(servings.trim())) l.serves else l.makes

/** The amounts picked, as a name: ½×, 2×. */
fun factorName(f: Double) = FACTORS.firstOrNull { it.first == f }?.second ?: ((if (f == Math.floor(f)) f.toLong().toString() else f.toString()) + "×")

/** "Nov 4", or "Nov 4, 2025" in another year; Hebrew months for Hebrew recipes. */
fun day(
    t: Double?,
    lang: String? = null,
): String {
    if (t == null || t <= 0) return ""
    val d = Date((t * 1000).toLong())
    val cal = Calendar.getInstance().apply { time = d }
    val sameYear = cal.get(Calendar.YEAR) == Calendar.getInstance().get(Calendar.YEAR)
    val loc = if (lang == "he") Locale.forLanguageTag("he-IL") else Locale.getDefault()
    val pattern = if (lang == "he") (if (sameYear) "d בMMM" else "d בMMM yyyy") else (if (sameYear) "MMM d" else "MMM d, yyyy")
    return SimpleDateFormat(pattern, loc).format(d)
}
