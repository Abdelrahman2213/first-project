package com.athaan.app.data

/** Ornament drawn in the corner of the "next prayer" card. */
enum class HeroPattern(val ar: String, val en: String) {
    MOSQUE("القبة والمآذن", "Dome & minarets"),
    MIHRAB("المحراب والقنديل", "Mihrab & lamp"),
    CRESCENT("الهلال", "Crescent"),
    ARABESQUE("أرابيسك نباتي", "Arabesque vine"),
    MASHRABIYA("المشربية", "Mashrabiya"),
    NONE("بدون زخرفة", "No ornament"),
}

/** Colour sets for the light theme. */
enum class LightPalette(val ar: String, val en: String) {
    IVORY("عاجي وذهبي", "Ivory & gold"),
    EMERALD("رملي وزمردي", "Sand & emerald"),
    TURQUOISE("لؤلؤي وفيروزي", "Pearl & turquoise"),
    DAWN("شروق وردي", "Dawn rose"),
    INDIGO("نيلي وذهبي", "Indigo & gold"),
}

/** How much of the recording to play at prayer time. */
enum class AdhanLength(val ar: String, val en: String, val maxMs: Long) {
    FULL("كامل", "Full", Long.MAX_VALUE),
    SHORT("30 ثانية", "30 sec", 30_000),
    TAKBIR("التكبير فقط", "Takbir only", 17_000),
}
