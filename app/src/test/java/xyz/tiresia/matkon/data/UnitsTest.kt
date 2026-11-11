package xyz.tiresia.matkon.data

import org.junit.Assert.assertEquals
import org.junit.Test

/** The same answers as the web app's units.js (worked out with it, for these very cases). */
class UnitsTest {
    private fun a(
        amount: String,
        mode: String,
        lang: String,
        want: String,
    ) = assertEquals("$amount in $mode", want, Units.amount(amount, mode, lang))

    private fun t(
        text: String,
        mode: String,
        lang: String,
        want: String,
    ) = assertEquals("$text in $mode", want, Units.text(text, mode, lang))

    @Test fun spoonsStaySpoons() {
        a("1 tbsp", "metric", "en", "1 tbsp")
        a("2 tsp", "us", "en", "2 tsp")
    }

    @Test fun bothGivenStays() = a("3 cups (720ml)", "metric", "en", "3 cups (720ml)")

    @Test fun asWrittenChangesNothing() {
        a("1 cup", "", "en", "1 cup")
        t("Bake at 350°F", "", "en", "Bake at 350°F")
    }

    @Test fun amounts() {
        a("1 cup", "metric", "en", "240 ml")
        a("2 cups", "metric", "en", "480 ml")
        a("1/2 cup", "metric", "en", "120 ml")
        a("1 tbsp", "metric", "en", "1 tbsp")
        a("1 TBSP", "metric", "en", "1 TBSP")
        a("3 cups (720ml)", "metric", "en", "3 cups (720ml)")
        a("200g", "us", "en", "7 oz")
        a("500 g", "us", "en", "1 lb")
        a("1 lb", "metric", "en", "450 g")
        a("2 lbs", "metric", "en", "910 g")
        a("1 kg", "us", "en", "2¼ lb")
        a("250 ml", "us", "en", "1 cup")
        a("100 ml", "us", "en", "½ cup")
        a("30 ml", "us", "en", "2 tbsp")
        a("5 ml", "us", "en", "1 tsp")
        a("2-3 cups", "metric", "en", "480-720 ml")
        a("about 2 cups", "metric", "en", "about 480 ml")
        a("2 כוסות", "metric", "he", "480 מ״ל")
        a("1 כוס", "metric", "he", "240 מ״ל")
        a("2 inches", "metric", "en", "5 cm")
        a("10 cm", "us", "en", "4 inch")
        a("1 liter", "us", "en", "4¼ cups")
        a("1½ cups", "metric", "en", "360 ml")
        a("to taste", "metric", "en", "to taste")
        a("", "metric", "en", "")
        a("1 cup", "", "en", "1 cup")
        a("2 carrots", "metric", "en", "2 carrots")
        a("1 can (14 oz)", "metric", "en", "1 can (14 oz)")
        a("170g (¾ cup)", "us", "en", "170g (¾ cup)")
        a("8 oz", "metric", "en", "230 g")
        a("1 1/2 lb", "metric", "en", "680 g")
        a("2 l", "us", "en", "8¼ cups")
        a("1.5 kg", "us", "en", "3¼ lb")
        a("3 oz", "metric", "en", "85 g")
        a("2 Cups", "metric", "en", "480 ml")
        a("200 גרם", "us", "he", "7 oz")
        a("1 ליטר", "us", "he", "4¼ כוסות")
        a("4 cups", "metric", "en", "960 ml")
        a("2 cups (480ml)", "us", "en", "2 cups (480ml)")
        a("1 cup (240 ml)", "metric", "en", "1 cup (240 ml)")
        a("16 fl oz", "metric", "en", "475 ml")
        a("1 pint", "metric", "en", "475 ml")
        a("12 oz", "metric", "en", "340 g")
        a("1 1/4 cups", "metric", "en", "300 ml")
        a("⅓ cup", "metric", "en", "80 ml")
        a("60 ml", "us", "en", "¼ cup")
        a("45 ml", "us", "en", "3 tbsp")
        a("1 kg", "metric", "en", "1 kg")
        a("0.5 kg", "us", "en", "1 lb")
        a("3/4 cup", "metric", "he", "180 מ״ל")
        a("700 ml", "us", "en", "3 cups")
        a("2 tsp", "us", "en", "2 tsp")
        a("2 קילו", "us", "he", "4½ lb")
        a("כ-2 כוסות", "metric", "he", "כ-480 מ״ל")
    }

    @Test fun texts() {
        t("Bake at 350°F for 20 minutes", "metric", "en", "Bake at 180°C for 20 minutes")
        t("Bake at 180°C", "us", "en", "Bake at 350°F")
        t("Preheat the oven to 400 degrees", "metric", "en", "Preheat the oven to 200°C")
        t("Preheat to 200 degrees", "us", "en", "Preheat to 400°F")
        t("180°C / 350°F", "metric", "en", "180°C / 350°F")
        t("180°C / 350°F", "us", "en", "180°C / 350°F")
        t("Add 2 cups of flour", "metric", "en", "Add 480 ml of flour")
        t("Add 1 tbsp oil", "metric", "en", "Add 1 tbsp oil")
        t("מחממים תנור ל-180 מעלות", "us", "he", "מחממים תנור ל-350°F")
        t("Bake at 350 F", "metric", "en", "Bake at 180°C")
        t("bake at 220 celsius", "us", "en", "bake at 425°F")
        t("Cook 2 inches apart", "metric", "en", "Cook 5 cm apart")
        t("Use 500 g pasta", "us", "en", "Use 1 lb pasta")
        t("350°F", "metric", "en", "180°C")
        t("Roast at 425 degrees F for 1 hour", "metric", "en", "Roast at 220°C for 1 hour")
        t("Pour in 1 cup (240ml) milk and 2 cups water", "metric", "en", "Pour in 240 ml (240ml) milk and 480 ml water")
        t("אופים ב-180 מעלות צלזיוס כ-25 דקות", "us", "he", "אופים ב-180 מעלות צלזיוס כ-25 דקות")
        t("מוסיפים 2 כוסות קמח ו-100 גרם חמאה", "us", "he", "מוסיפים 2 כוסות קמח ו-3½ oz חמאה")
        t("Bake at 180 C until golden", "us", "en", "Bake at 350°F until golden")
        t("Heat 2-3 tbsp oil", "metric", "en", "Heat 2-3 tbsp oil")
        t("Add 8 oz cheese and 1 lb beef", "metric", "en", "Add 230 g cheese and 450 g beef")
        t("Nothing to change here", "metric", "en", "Nothing to change here")
        t("Bake at 350°F", "", "en", "Bake at 350°F")
        t("Chill 30 minutes, then bake at 375°F (190°C)", "metric", "en", "Chill 30 minutes, then bake at 375°F (190°C)")
    }
}
