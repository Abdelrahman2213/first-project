package com.athaan.app.data

/** Ornament drawn in the corner of the "next prayer" card. */
enum class HeroPattern(val ar: String, val en: String) {
    SHAMSA("الشمسة", "Shamsa medallion"),
    RUB_EL_HIZB("ربع الحزب ۞", "Rub el Hizb ۞"),
    ALHAMBRA("نجمة وصليب · الحمراء", "Star & cross · Alhambra"),
    MAMLUK("النجمة العشرية المملوكية", "Mamluk ten-point star"),
    ROSETTE("الوردة الهندسية", "Geometric rosette"),
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
