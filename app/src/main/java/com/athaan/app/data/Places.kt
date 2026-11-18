package com.athaan.app.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.Normalizer
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.cos
import kotlin.math.sqrt

data class Country(val code: String, val nameEn: String, val cityCount: Int) {
    /** Country name in the app language (from Android's own translations). */
    fun name(ar: Boolean): String {
        val loc = Locale("", code)
        val n = loc.getDisplayCountry(if (ar) Locale("ar") else Locale.ENGLISH)
        return if (n.isNullOrBlank() || n == code) nameEn else n
    }
}

data class City(
    val country: String,
    val nameEn: String,
    val nameAr: String,
    val lat: Double,
    val lng: Double,
    val timezone: String,
) {
    fun name(ar: Boolean) = if (ar && nameAr.isNotEmpty()) nameAr else nameEn
}

/**
 * World countries and cities (GeoNames, every place with 1,000+ people), bundled in assets
 * so manual selection works offline. Files: assets/cities/_countries.tsv and one <CC>.tsv per country.
 */
object Places {
    private val cityCache = ConcurrentHashMap<String, List<City>>()
    @Volatile private var countryCache: List<Country>? = null

    suspend fun countries(context: Context): List<Country> = withContext(Dispatchers.IO) {
        countryCache ?: context.assets.open("cities/_countries.tsv").bufferedReader().useLines { lines ->
            lines.filter { it.isNotBlank() }.map { l ->
                val p = l.split('\t')
                Country(p[0], p[1], p.getOrNull(2)?.toIntOrNull() ?: 0)
            }.toList()
        }.also { countryCache = it }
    }

    suspend fun cities(context: Context, code: String): List<City> = withContext(Dispatchers.IO) {
        cityCache[code] ?: runCatching {
            context.assets.open("cities/$code.tsv").bufferedReader().useLines { lines ->
                lines.filter { it.isNotBlank() }.mapNotNull { l ->
                    val p = l.split('\t')
                    if (p.size < 5) return@mapNotNull null
                    City(code, p[0], p[1], p[2].toDouble(), p[3].toDouble(), p[4])
                }.toList()
            }
        }.getOrDefault(emptyList()).also { cityCache[code] = it }
    }

    /**
     * The city a point belongs to: the biggest city within ~20 km (files are sorted by
     * population, so that is the first match), otherwise the closest one within 150 km.
     */
    suspend fun nearest(context: Context, lat: Double, lng: Double, countryHint: String? = null): City? = withContext(Dispatchers.IO) {
        val codes = if (countryHint != null) listOf(countryHint) else countries(context).map { it.code }
        val k = cos(Math.toRadians(lat))
        fun km(c: City): Double {
            val dx = (c.lng - lng) * k
            val dy = c.lat - lat
            return sqrt(dx * dx + dy * dy) * 111.0
        }
        var closest: City? = null
        var closestKm = Double.MAX_VALUE
        for (cc in codes) {
            val list = cities(context, cc)
            list.firstOrNull { km(it) < 20.0 }?.let { big ->
                if (countryHint != null) return@withContext big
                if (km(big) < closestKm) { closest = big; closestKm = km(big) }
            }
            for (c in list) {
                val d = km(c)
                if (d < closestKm) { closestKm = d; closest = c }
            }
            if (countryHint == null) cityCache.remove(cc) // keep memory low on a full scan
        }
        closest?.takeIf { closestKm < 150 }
    }

    /** Lower-cases, strips accents and unifies Arabic letter forms, for search. */
    fun normalize(s: String): String {
        val n = Normalizer.normalize(s.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
        val sb = StringBuilder(n.length)
        for (ch in n) {
            sb.append(
                when (ch) {
                    'أ', 'إ', 'آ', 'ٱ' -> 'ا'
                    'ة' -> 'ه'
                    'ى' -> 'ي'
                    'ؤ' -> 'و'
                    'ئ' -> 'ي'
                    'ـ' -> ' '
                    else -> ch
                }
            )
        }
        return sb.toString().replace("-", " ").trim()
    }
}
