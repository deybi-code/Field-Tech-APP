package com.huertointeligente.app.domain

import androidx.annotation.StringRes
import com.huertointeligente.app.R
import com.huertointeligente.app.data.model.Huerto

data class Range(val min: Float, val max: Float) {
    val mid: Float get() = (min + max) / 2f
    operator fun contains(v: Float): Boolean = v >= min && v <= max
}

/** Valores referenciales por cultivo (validar con un ingeniero agrónomo). */
data class Crop(
    val id: String,
    @StringRes val nameRes: Int,
    @StringRes val descRes: Int,
    @StringRes val tipRes: Int,
    val soil: Range,
    val airTemp: Range,
    val airHumidity: Range
)

object Crops {

    val generic = Crop(
        "generic", R.string.crop_generic, R.string.crop_generic_desc, R.string.crop_generic_tip,
        soil = Range(40f, 75f), airTemp = Range(12f, 32f), airHumidity = Range(40f, 85f)
    )

    val all: List<Crop> = listOf(
        Crop(
            "asparagus", R.string.crop_asparagus, R.string.crop_asparagus_desc, R.string.crop_asparagus_tip,
            soil = Range(55f, 75f), airTemp = Range(15f, 28f), airHumidity = Range(50f, 75f)
        ),
        Crop(
            "blueberry", R.string.crop_blueberry, R.string.crop_blueberry_desc, R.string.crop_blueberry_tip,
            soil = Range(60f, 80f), airTemp = Range(15f, 26f), airHumidity = Range(60f, 80f)
        ),
        Crop(
            "avocado", R.string.crop_avocado, R.string.crop_avocado_desc, R.string.crop_avocado_tip,
            soil = Range(55f, 75f), airTemp = Range(18f, 30f), airHumidity = Range(50f, 80f)
        ),
        Crop(
            "grape", R.string.crop_grape, R.string.crop_grape_desc, R.string.crop_grape_tip,
            soil = Range(45f, 65f), airTemp = Range(18f, 32f), airHumidity = Range(40f, 65f)
        ),
        Crop(
            "artichoke", R.string.crop_artichoke, R.string.crop_artichoke_desc, R.string.crop_artichoke_tip,
            soil = Range(55f, 75f), airTemp = Range(12f, 24f), airHumidity = Range(60f, 80f)
        ),
        Crop(
            "pepper", R.string.crop_pepper, R.string.crop_pepper_desc, R.string.crop_pepper_tip,
            soil = Range(60f, 80f), airTemp = Range(18f, 30f), airHumidity = Range(50f, 70f)
        ),
        Crop(
            "corn", R.string.crop_corn, R.string.crop_corn_desc, R.string.crop_corn_tip,
            soil = Range(50f, 70f), airTemp = Range(18f, 32f), airHumidity = Range(50f, 75f)
        ),
        Crop(
            "sugarcane", R.string.crop_sugarcane, R.string.crop_sugarcane_desc, R.string.crop_sugarcane_tip,
            soil = Range(60f, 80f), airTemp = Range(22f, 34f), airHumidity = Range(60f, 85f)
        )
    )

    fun byId(id: String?): Crop? = all.firstOrNull { it.id == id }

    /** Cultivo del huerto, o el perfil general si aún no se definió. */
    fun forHuerto(h: Huerto?): Crop = byId(h?.cropId) ?: generic
}
