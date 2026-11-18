package com.athaan.app.data

/**
 * The muezzin library. Each recording is streamed from its URL and can be downloaded
 * to the phone for offline playback. To add a sheikh, add one line here.
 *
 * NOTE: these recordings come from public GitHub repositories (see README). Check the
 * rights to each recording before publishing the app on Google Play.
 */
data class Muezzin(
    val id: String,
    val nameAr: String,
    val nameEn: String,
    val originAr: String,
    val originEn: String,
    val region: Region,
    val url: String,
    val fajrOnly: Boolean = false,
) {
    val initial: String get() = nameAr.removePrefix("أذان ").trim().removePrefix("ال").take(1)

    fun name(ar: Boolean) = if (ar) nameAr else nameEn
    fun origin(ar: Boolean) = if (ar) originAr else originEn
}

enum class Region(val ar: String, val en: String) {
    EGYPT("مصر", "Egypt"),
    HARAMAIN("الحرمين", "Haramain"),
    GULF("الخليج", "Gulf"),
    OTHER("أخرى", "Other"),
}

object Muezzins {
    private const val ABODE = "https://raw.githubusercontent.com/abodehq/Athan-MP3/master/Sounds/"

    val all: List<Muezzin> = listOf(
        Muezzin("basit", "عبد الباسط عبد الصمد", "Abdul Basit Abdus Samad", "مصر", "Egypt", Region.EGYPT, ABODE + "Athan%20Abed%20Albase6.mp3"),
        Muezzin("refaat", "محمد رفعت", "Mohamed Refaat", "مصر", "Egypt", Region.EGYPT, ABODE + "Athan%20Mohammad%20Ref3at.mp3"),
        Muezzin("minshawi", "محمد صديق المنشاوي", "Mohamed Siddiq Al-Minshawi", "مصر", "Egypt", Region.EGYPT, ABODE + "Athan%20Mohammad%20Almenshawy.mp3"),
        Muezzin("makkah", "أذان الحرم المكي", "Makkah Haram adhan", "مكة المكرمة", "Makkah", Region.HARAMAIN, ABODE + "Athan%20Makkah.mp3"),
        Muezzin("madinah", "أذان المسجد النبوي", "Masjid an-Nabawi adhan", "المدينة المنورة", "Madinah", Region.HARAMAIN, "https://raw.githubusercontent.com/meypod/al-azan/main/src/assets/sounds/masjid_an_nabawi.mp3"),
        Muezzin("aqsa", "أذان المسجد الأقصى", "Al-Aqsa adhan", "القدس", "Jerusalem", Region.OTHER, "https://raw.githubusercontent.com/DBChoco/Muezzin/master/ressources/audio/Adhan%20-%20al-Aqsa.mp3"),
        Muezzin("afasy", "مشاري العفاسي", "Mishary Alafasy", "الكويت", "Kuwait", Region.GULF, ABODE + "Athan%20Mishary%20Alafasi.mp3"),
        Muezzin("qatami", "ناصر القطامي", "Nasser Al-Qatami", "السعودية", "Saudi Arabia", Region.GULF, ABODE + "Athan%20Nasser%20Alqatami.mp3"),
        Muezzin("zahrani", "منصور الزهراني", "Mansour Az-Zahrani", "السعودية", "Saudi Arabia", Region.GULF, ABODE + "Athan%20Mansoor%20Az-Zahrani.mp3"),
        Muezzin("nufais", "أحمد النفيس", "Ahmed Al-Nufais", "الكويت", "Kuwait", Region.GULF, ABODE + "Athan%20Ahmad%20Nuyne3.mp3"),
        Muezzin("deghreri", "حمد الدغريري", "Hamad Al-Deghreri", "السعودية", "Saudi Arabia", Region.GULF, ABODE + "Athan%20Hamad%20Deghreri.mp3"),
        Muezzin("malki", "حمدان المالكي", "Hamdan Al-Malki", "السعودية", "Saudi Arabia", Region.GULF, ABODE + "Athan%20Hamdan%20Almalki.mp3"),
        Muezzin("arkani", "إبراهيم الأركاني", "Ibrahim Al-Arkani", "السعودية", "Saudi Arabia", Region.GULF, ABODE + "Athan%20Ibrahim%20Al-Arkani.mp3"),
        Muezzin("hamadani", "ماجد الهمداني", "Majed Al-Hamathani", "السعودية", "Saudi Arabia", Region.GULF, ABODE + "Athan%20Majed%20Al-hamathani.mp3"),
        Muezzin("khatba", "صهيب خطبا", "Suhaib Khatba", "", "", Region.OTHER, ABODE + "Athan%20Suhaib%20Khatba.mp3"),
        Muezzin("chebae", "مالك شيبة (أذان الفجر)", "Malek Chebae (Fajr adhan)", "أذان الفجر", "Fajr adhan", Region.OTHER, ABODE + "Athan%20Al-fajer%20-%20Malek%20chebae.mp3", fajrOnly = true),
    )

    fun byId(id: String?): Muezzin = all.firstOrNull { it.id == id } ?: all.first()
}
