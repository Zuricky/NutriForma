package com.nutriforma.app.domain.model

/** Meal slots of the day (SPEC §1.1). [order] drives every sort by slot. */
enum class MealSlot(
    val labelIt: String,
    val defaultTimeMinutes: Int,
    val windowEndMinutes: Int?,
    val order: Int,
) {
    COLAZIONE("Colazione", 450, null, 0),
    META_MATTINA("Metà mattina", 630, null, 1),
    PRANZO("Pranzo", 840, 960, 2),
    META_POMERIGGIO("Metà pomeriggio", 1050, null, 3),
    CENA("Cena", 1230, null, 4),
}

/** One component "slot" inside a meal (SPEC §1.2). */
enum class MealRole(val labelIt: String) {
    COLAZIONE_G1("Proteine"),
    COLAZIONE_G2("Carboidrati"),
    COLAZIONE_DOLCIFICANTE("Miele o marmellata"),
    COLAZIONE_BEVANDA("Bevanda"),
    COLAZIONE_DOLCE("Dolce"),
    COLAZIONE_DOLCE_BEVANDA("Bevanda"),
    SPUNTINO_SECCA("Frutta secca"),
    SPUNTINO_FRUTTA("Frutta fresca"),
    SPUNTINO_ABBINAMENTO("Abbinamento"),
    SPUNTINO_BARRETTA("Barretta proteica"),
    PRANZO_ANTIPASTO("Antipasto di verdure"),
    PRANZO_SECONDO("Secondo piatto"),
    PRANZO_ACCOMPAGNAMENTO("Accompagnamento"),
    PRANZO_CONDIMENTO("Condimento"),
    PRANZO_LIBERO("Pasto libero"),
    CENA_VERDURE("Verdure"),
    CENA_PRIMO_BASE("Primo piatto"),
    CENA_PRIMO_CONDIMENTO_VERDURE("Condimento di verdure"),
    CENA_PRIMO_GRANA("Formaggio grattugiato"),
    CENA_PRIMO_CONDIMENTO_PROTEICO("Condimento proteico"),
    CENA_SECONDO("Secondo piatto"),
    CENA_SECONDO_ACCOMPAGNAMENTO("Accompagnamento"),
    CENA_MINESTRONE("Minestrone"),
    CENA_MINESTRONE_AGGIUNTA("Aggiunta proteica"),
    CENA_CONDIMENTO("Condimento"),
}

/** Food category: drives counting, templates and portions (SPEC §1.3). */
enum class FoodCategory(val labelIt: String) {
    CARNE_BIANCA("Carne bianca"),
    CARNE_ROSSA("Carne rossa"),
    PESCE("Pesce"),
    PESCE_AFFUMICATO("Pesce affumicato"),
    TONNO_SGOMBRO("Tonno/sgombro sott'olio"),
    UOVA("Uova"),
    AFFETTATO("Affettato"),
    LATTICINI("Latticini"),
    FORMAGGIO_STAGIONATO("Formaggio stagionato"),
    LEGUMI("Legumi"),
    PANE("Pane"),
    FETTE_BISCOTTATE("Fette biscottate"),
    CEREALI_COLAZIONE("Cereali per colazione"),
    CEREALE_CHICCO("Cereale in chicco"),
    PASTA("Pasta"),
    POLENTA("Polenta"),
    PATATE("Patate"),
    FRUTTA_FRESCA("Frutta fresca"),
    FRUTTA_SECCA("Frutta secca"),
    MIX_FRUTTA("Mix di frutta secca e disidratata"),
    VERDURE("Verdure"),
    MINESTRONE("Minestrone"),
    SALSA_POMODORO("Passata di pomodoro"),
    RAGU("Ragù"),
    PESTO("Pesto"),
    OLIO("Olio"),
    DOLCIFICANTE("Miele e marmellata"),
    BEVANDA("Bevande"),
    DOLCE_COLAZIONE("Dolci da colazione"),
    CIOCCOLATO("Cioccolato fondente"),
    AVOCADO("Avocado"),
    BARRETTA_PROTEICA("Barretta proteica"),
    PASTO_LIBERO("Pasto libero"),
}

/** Meal types (SPEC §1.4). [slot] is null for the turno-lungo types that can live in either snack slot. */
enum class MealType(val labelIt: String, val slot: MealSlot?) {
    COLAZIONE_STANDARD("Colazione", MealSlot.COLAZIONE),
    COLAZIONE_DOLCE("Colazione dolce", MealSlot.COLAZIONE),
    SPUNTINO_MATTINA("Spuntino", MealSlot.META_MATTINA),
    SPUNTINO_POMERIGGIO("Spuntino", MealSlot.META_POMERIGGIO),
    SPUNTINO_TURNO_LUNGO("Spuntino unico (turno lungo)", null),
    BARRETTA_TURNO_LUNGO("Barretta proteica (turno lungo)", null),
    SPUNTINO_SALTATO("Spuntino accorpato (turno lungo)", null),
    PRANZO_STANDARD("Pranzo", MealSlot.PRANZO),
    PRANZO_LIBERO("Pasto libero", MealSlot.PRANZO),
    CENA_PRIMO_VERDURE("Primo con verdure", MealSlot.CENA),
    CENA_PRIMO_PROTEICO("Primo con condimento proteico", MealSlot.CENA),
    CENA_SECONDO("Secondo piatto", MealSlot.CENA),
    CENA_MINESTRONE("Minestrone completo", MealSlot.CENA),
}

/** Unit of a planned quantity (SPEC §1.5). */
enum class PortionUnit(val singularIt: String, val pluralIt: String) {
    GRAMMI("g", "g"),
    PEZZI("pz", "pz"),
    CUCCHIAINI("cucchiaino", "cucchiaini"),
    CUCCHIAI("cucchiaio", "cucchiai"),
    TAZZINA("tazzina", "tazzine"),
    TAZZA("tazza", "tazze"),
    PIATTO("piatto", "piatti"),
    PORZIONE("porzione", "porzioni"),
}

enum class Goal(val labelIt: String) {
    DIMAGRIMENTO("Dimagrimento"),
    EQUILIBRIO("Equilibrio alimentare"),
    ENERGIA("Energia"),
}

enum class SportLevel(
    val labelIt: String,
    val portionFactor: Double,
    val activityFactor: Double,
    val proteinGPerKg: Double,
) {
    SEDENTARIO("Sedentario", 1.00, 1.20, 1.0),
    LEGGERO("Leggero (1-2 allenamenti/sett.)", 1.10, 1.375, 1.2),
    MODERATO("Moderato (3-4 allenamenti/sett.)", 1.20, 1.55, 1.4),
    INTENSO("Intenso (5-6 allenamenti/sett.)", 1.30, 1.725, 1.6),
    MOLTO_INTENSO("Molto intenso (allenamento quotidiano)", 1.40, 1.90, 1.8),
}

enum class Sesso(val labelIt: String) {
    MASCHIO("Uomo"),
    FEMMINA("Donna"),
}

enum class TurnoLungoOption(val labelIt: String) {
    FRUTTA_E_SECCA("Frutta fresca + frutta secca"),
    BARRETTA("Barretta proteica"),
}

enum class SupermarketCategory(val labelIt: String, val order: Int) {
    FRUTTA_VERDURA("Frutta e verdura", 0),
    CARNE("Macelleria", 1),
    PESCE("Pescheria", 2),
    SALUMI_FORMAGGI("Salumi e formaggi", 3),
    LATTICINI_UOVA("Latte, yogurt e uova", 4),
    PANE_FORNO("Pane e forno", 5),
    PASTA_RISO_CEREALI("Pasta, riso e cereali", 6),
    CONSERVE_SUGHI("Conserve e sughi", 7),
    FRUTTA_SECCA_SNACK("Frutta secca e snack", 8),
    SURGELATI("Surgelati", 9),
    DISPENSA("Dispensa", 10),
    BEVANDE("Caffè e bevande", 11),
}

enum class ComplianceStatus { OK, ATTENZIONE, VIOLAZIONE }

/** Weekly rules (SPEC §4.2 counters, then §4.3 soft quality rules). Declaration order = report order. */
enum class RuleId(val labelIt: String) {
    COLAZIONE_DOLCE("Colazioni dolci"),
    PASTO_LIBERO("Pasti liberi"),
    CENA_PRIMO("Cene con primo piatto"),
    CENA_PRIMO_VERDURE("Primi con verdure o passata"),
    CENA_PRIMO_PROTEICO("Primi con condimento proteico"),
    CENA_SECONDO("Cene con secondo piatto"),
    CENA_MINESTRONE("Minestrone completo"),
    CARNE("Carne"),
    CARNE_ROSSA("Carne rossa"),
    PESCE("Pesce"),
    UOVA_OCCASIONI("Uova (volte)"),
    UOVA_TOTALE("Uova (totale)"),
    TONNO("Tonno o sgombro sott'olio"),
    AFFETTATO("Affettato"),
    PATATE("Patate"),
    CARNE_BIANCA_PREFERITA("Preferire carni bianche"),
    ALTERNANZA_META_MATTINA("Alternanza frutta secca / mix"),
    VERDURE_CENA_DIVERSE("Verdure di cena diverse dal pranzo"),
}

enum class WarningCode {
    NESSUN_ALIMENTO_PER_RUOLO,
    CATEGORIA_SOSTITUITA,
    COLAZIONE_DOLCE_NON_DISPONIBILE,
    OBIETTIVO_NON_RAGGIUNGIBILE,
}
