package com.nutriforma.app.ui.food

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nutriforma.app.R
import com.nutriforma.app.domain.model.FoodCategory
import com.nutriforma.app.domain.model.FoodItem

/** Icone vettoriali degli alimenti (VectorDrawable in `res/drawable`, palette mediterranea). */
enum class FoodIcon(@param:DrawableRes val resId: Int) {
    CHICKEN(R.drawable.ic_food_chicken),
    STEAK(R.drawable.ic_food_steak),
    FISH(R.drawable.ic_food_fish),
    SALMON(R.drawable.ic_food_salmon),
    CAN(R.drawable.ic_food_can),
    EGG(R.drawable.ic_food_egg),
    COLD_CUTS(R.drawable.ic_food_cold_cuts),
    DAIRY(R.drawable.ic_food_dairy),
    CHEESE(R.drawable.ic_food_cheese),
    LEGUMES(R.drawable.ic_food_legumes),
    BREAD(R.drawable.ic_food_bread),
    RUSK(R.drawable.ic_food_rusk),
    CEREAL(R.drawable.ic_food_cereal),
    GRAIN(R.drawable.ic_food_grain),
    PASTA(R.drawable.ic_food_pasta),
    POLENTA(R.drawable.ic_food_polenta),
    POTATO(R.drawable.ic_food_potato),
    APPLE(R.drawable.ic_food_apple),
    NUT(R.drawable.ic_food_nut),
    MIX(R.drawable.ic_food_mix),
    VEGETABLE(R.drawable.ic_food_vegetable),
    SOUP(R.drawable.ic_food_soup),
    TOMATO(R.drawable.ic_food_tomato),
    RAGU(R.drawable.ic_food_ragu),
    PESTO(R.drawable.ic_food_pesto),
    OIL(R.drawable.ic_food_oil),
    HONEY(R.drawable.ic_food_honey),
    COFFEE(R.drawable.ic_food_coffee),
    PASTRY(R.drawable.ic_food_pastry),
    CHOCOLATE(R.drawable.ic_food_chocolate),
    AVOCADO(R.drawable.ic_food_avocado),
    BAR(R.drawable.ic_food_bar),
    FREE_MEAL(R.drawable.ic_food_free_meal),
    DISH(R.drawable.ic_food_dish),
    POLPO(R.drawable.ic_food_polpo),
    COOKIE(R.drawable.ic_food_cookie),
    PEANUT_BUTTER(R.drawable.ic_food_peanut_butter),
    JUICE(R.drawable.ic_food_juice),
    LASAGNA(R.drawable.ic_food_lasagna),
    CARBONARA(R.drawable.ic_food_carbonara),
    GNOCCHI(R.drawable.ic_food_gnocchi),
    TORTELLINI(R.drawable.ic_food_tortellini),
    LETTUCE(R.drawable.ic_food_lettuce),

    /** Voci aggiunte a mano alla spesa (nessun alimento del catalogo). */
    BASKET(R.drawable.ic_food_basket),
}

/** Dall'alimento (o dalla sua categoria) all'icona da mostrare. Nessuna dipendenza da `domain/`. */
object FoodIcons {

    /** Icone specifiche per singolo alimento; hanno la precedenza sulla categoria. */
    private val byFoodId: Map<String, FoodIcon> = mapOf(
        "polpo" to FoodIcon.POLPO,
        "biscotti" to FoodIcon.COOKIE,
        "burro_arachidi" to FoodIcon.PEANUT_BUTTER,
        "spremuta_arancia" to FoodIcon.JUICE,
        "pasticcio" to FoodIcon.LASAGNA,
        "carbonara" to FoodIcon.CARBONARA,
        "gnocchi_ragu" to FoodIcon.GNOCCHI,
        "tortellini_brodo" to FoodIcon.TORTELLINI,
        "insalata_mista" to FoodIcon.LETTUCE,
        "pomodori" to FoodIcon.TOMATO,
    )

    /** Un'icona per ogni [FoodCategory]: il `when` esaustivo obbliga ad aggiornarla se la categoria cresce. */
    fun forCategory(category: FoodCategory): FoodIcon = when (category) {
        FoodCategory.CARNE_BIANCA -> FoodIcon.CHICKEN
        FoodCategory.CARNE_ROSSA -> FoodIcon.STEAK
        FoodCategory.PESCE -> FoodIcon.FISH
        FoodCategory.PESCE_AFFUMICATO -> FoodIcon.SALMON
        FoodCategory.TONNO_SGOMBRO -> FoodIcon.CAN
        FoodCategory.UOVA -> FoodIcon.EGG
        FoodCategory.AFFETTATO -> FoodIcon.COLD_CUTS
        FoodCategory.LATTICINI -> FoodIcon.DAIRY
        FoodCategory.FORMAGGIO_STAGIONATO -> FoodIcon.CHEESE
        FoodCategory.LEGUMI -> FoodIcon.LEGUMES
        FoodCategory.PANE -> FoodIcon.BREAD
        FoodCategory.FETTE_BISCOTTATE -> FoodIcon.RUSK
        FoodCategory.CEREALI_COLAZIONE -> FoodIcon.CEREAL
        FoodCategory.CEREALE_CHICCO -> FoodIcon.GRAIN
        FoodCategory.PASTA -> FoodIcon.PASTA
        FoodCategory.POLENTA -> FoodIcon.POLENTA
        FoodCategory.PATATE -> FoodIcon.POTATO
        FoodCategory.FRUTTA_FRESCA -> FoodIcon.APPLE
        FoodCategory.FRUTTA_SECCA -> FoodIcon.NUT
        FoodCategory.MIX_FRUTTA -> FoodIcon.MIX
        FoodCategory.VERDURE -> FoodIcon.VEGETABLE
        FoodCategory.MINESTRONE -> FoodIcon.SOUP
        FoodCategory.SALSA_POMODORO -> FoodIcon.TOMATO
        FoodCategory.RAGU -> FoodIcon.RAGU
        FoodCategory.PESTO -> FoodIcon.PESTO
        FoodCategory.OLIO -> FoodIcon.OIL
        FoodCategory.DOLCIFICANTE -> FoodIcon.HONEY
        FoodCategory.BEVANDA -> FoodIcon.COFFEE
        FoodCategory.DOLCE_COLAZIONE -> FoodIcon.PASTRY
        FoodCategory.CIOCCOLATO -> FoodIcon.CHOCOLATE
        FoodCategory.AVOCADO -> FoodIcon.AVOCADO
        FoodCategory.BARRETTA_PROTEICA -> FoodIcon.BAR
        FoodCategory.PASTO_LIBERO -> FoodIcon.FREE_MEAL
        FoodCategory.PIATTO_UNICO -> FoodIcon.DISH
    }

    fun forFoodId(foodId: String, category: FoodCategory?): FoodIcon =
        byFoodId[foodId] ?: category?.let(::forCategory) ?: FoodIcon.DISH

    fun forFood(food: FoodItem): FoodIcon = forFoodId(food.id, food.category)
}

/**
 * Icona decorativa: il nome dell'alimento è sempre scritto accanto, quindi non ha descrizione per i lettori di schermo.
 * Le dimensioni seguono la scala del testo (fino a 1,5×) così icona e testo restano bilanciati con caratteri grandi.
 */
@Composable
fun FoodIconImage(icon: FoodIcon, modifier: Modifier = Modifier, size: Dp = 28.dp) {
    val scale = LocalDensity.current.fontScale.coerceIn(1f, 1.5f)
    Box(
        modifier = modifier
            .size(size * scale + 8.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(icon.resId),
            contentDescription = null,
            modifier = Modifier.padding(4.dp).size(size * scale),
        )
    }
}
