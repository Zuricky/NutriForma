package com.nutriforma.app.domain.tips

import java.time.LocalDate

/** SPEC §10: 21 locked tips, one per day, rotating by epoch day. */
object DailyTips {

    val TIPS: List<String> = listOf(
        "Mangia lentamente e mastica bene: il senso di sazietà arriva dopo circa 20 minuti.",
        "Usa il sale con moderazione e insaporisci con erbe aromatiche e spezie.",
        "Bevi almeno 1,7 litri di acqua al giorno, distribuiti nell'arco della giornata.",
        "Preferisci le carni bianche: la carne rossa al massimo una volta a settimana.",
        "Porta il pesce in tavola almeno due volte a settimana.",
        "L'olio extravergine d'oliva è meglio a crudo: 3 cucchiaini a pasto.",
        "A pranzo inizia sempre con un antipasto di verdure crude o cotte.",
        "Scegli pane, pasta e riso preferibilmente integrali.",
        "A cena varia le verdure in base a quello che hai mangiato a pranzo.",
        "Alterna frutta secca e mix di frutta disidratata a metà mattina.",
        "Nel turno lungo unisci frutta fresca e frutta secca in un unico spuntino.",
        "Le uova sono un'ottima proteina: 5-6 a settimana, meglio con tuorlo morbido.",
        "Tonno e sgombro sott'olio: al massimo una volta a settimana.",
        "Le patate sono un accompagnamento, non una verdura: massimo una volta a settimana.",
        "Il pasto libero fa parte del percorso: goditelo senza sensi di colpa.",
        "Per la colazione abbina sempre una fonte proteica a una di carboidrati.",
        "Il cioccolato fondente almeno all'80% è un ottimo spuntino in piccole quantità.",
        "Pesati sempre nelle stesse condizioni: al mattino, a digiuno, dopo il bagno.",
        "Misura vita e cosce una volta a settimana per vedere i progressi reali.",
        "Prepara la lista della spesa in anticipo: comprerai solo ciò che serve.",
        "Dopo l'allenamento non saltare il pasto successivo: aiuta il recupero.",
    )

    fun tipFor(date: LocalDate): String = TIPS[Math.floorMod(date.toEpochDay(), TIPS.size.toLong()).toInt()]
}
