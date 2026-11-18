package com.athaan.app.data

import com.athaan.app.prayer.Prayer

/**
 * One recording to play: a muezzin's regular adhan, or his Fajr adhan
 * (with "الصلاة خير من النوم").
 */
data class Track(val muezzin: Muezzin, val fajr: Boolean) {
    val key: String get() = if (fajr) "${muezzin.id}_fajr" else muezzin.id
    val url: String get() = (if (fajr) muezzin.fajrUrl else muezzin.url) ?: muezzin.url ?: muezzin.fajrUrl!!
}

/**
 * A muezzin and his recordings. [url] is the regular adhan (Dhuhr, Asr, Maghrib, Isha);
 * [fajrUrl] is the Fajr adhan. Either may be missing.
 *
 * Recordings come from public GitHub repositories (see README). Check the rights to each
 * recording before publishing the app on Google Play.
 */
data class Muezzin(
    val id: String,
    val nameAr: String,
    val nameEn: String,
    val originAr: String,
    val originEn: String,
    val region: Region,
    val url: String?,
    val fajrUrl: String?,
) {
    val hasRegular: Boolean get() = url != null
    val hasFajr: Boolean get() = fajrUrl != null
    /** A collective recording (e.g. "أذان الحرم المكي") rather than a named sheikh. */
    val isPlace: Boolean get() = nameAr.startsWith("أذان")
    val isCustom: Boolean get() = region == Region.CUSTOM

    val initial: String
        get() = nameAr.removePrefix("أذان ").removePrefix("الشيخ ").trim().removePrefix("ال").take(1).ifEmpty { "؟" }

    fun name(ar: Boolean) = if (ar) nameAr else nameEn
    /** "الشيخ ..." for a person, plain name for a place. */
    fun title(ar: Boolean) = if (ar && !isPlace && !isCustom) "الشيخ $nameAr" else name(ar)
    fun origin(ar: Boolean) = if (ar) originAr else originEn

    fun canCall(p: Prayer) = if (p == Prayer.FAJR) hasFajr else hasRegular
    fun trackFor(p: Prayer) = Track(this, p == Prayer.FAJR)
    /** The recording to preview when no prayer is in context. */
    val defaultTrack: Track get() = Track(this, !hasRegular)
}

enum class Region(val ar: String, val en: String) {
    EGYPT("مصر", "Egypt"),
    HARAMAIN("الحرمين والأقصى", "Haramain & Aqsa"),
    GULF("الخليج", "Gulf"),
    OTHER("أخرى", "Other"),
    CUSTOM("أصواتي", "My recordings"),
}

object Muezzins {
    /** Built-in library followed by the user's own recordings. */
    val all: List<Muezzin> get() = builtIn + CustomMuezzins.list

    val builtIn: List<Muezzin> = listOf(
        Muezzin("basit", "عبد الباسط عبد الصمد", "Abdul Basit Abdus Samad", "مصر", "Egypt", Region.EGYPT, "https://raw.githubusercontent.com/Kiwifu/adhan-mp3/main/Abdulbasit_Abdusamad_1_-_Egypt_%28%D8%B9%D8%A8%D8%AF_%D8%A7%D9%84%D8%A8%D8%A7%D8%B3%D8%B7_%D8%B9%D8%A8%D8%AF_%D8%A7%D9%84%D8%B5%D9%85%D8%AF_-_%D9%85%D8%B5%D8%B1%29.mp3", "https://raw.githubusercontent.com/bilalelsayed97/adhan_sounds/main/fajr_abd_albaset.mp3"),
        Muezzin("refaat", "محمد رفعت", "Mohamed Refaat", "مصر", "Egypt", Region.EGYPT, "https://raw.githubusercontent.com/Kiwifu/adhan-mp3/main/Muhammad_Refaat_-_Cairo_%28%D9%85%D8%AD%D9%85%D8%AF_%D8%B1%D9%81%D8%B9%D8%AA_-_%D8%A7%D9%84%D9%82%D8%A7%D9%87%D8%B1%D8%A9%29.mp3", null),
        Muezzin("husary", "محمود خليل الحصري", "Mahmoud Khalil Al-Husary", "مصر", "Egypt", Region.EGYPT, "https://raw.githubusercontent.com/Kiwifu/adhan-mp3/main/Mahmoud_Khalil_Al_Hussary_-_Cairo_%28%D9%85%D8%AD%D9%85%D9%88%D8%AF_%D8%AE%D9%84%D9%8A%D9%84_%D8%A7%D9%84%D8%AD%D8%B5%D8%B1%D9%8A_-_%D8%A7%D9%84%D9%82%D8%A7%D9%87%D8%B1%D8%A9%29.mp3", null),
        Muezzin("minshawi", "محمد صديق المنشاوي", "Mohamed Siddiq Al-Minshawi", "مصر", "Egypt", Region.EGYPT, "https://raw.githubusercontent.com/Kiwifu/adhan-mp3/main/Mohamed_Siddiq_El-Minshawi_-_Egypt_1_%28%D9%85%D8%AD%D9%85%D8%AF_%D8%B5%D8%AF%D9%8A%D9%82_%D8%A7%D9%84%D9%85%D9%86%D8%B4%D8%A7%D9%88%D9%8A_-_%D9%85%D8%B5%D8%B1%29.mp3", null),
        Muezzin("mustafa_ismail", "مصطفى إسماعيل", "Mustafa Ismail", "مصر", "Egypt", Region.EGYPT, "https://raw.githubusercontent.com/Kiwifu/adhan-mp3/main/Mustafa_Ismail_-_Egypt_%28%D9%85%D8%B5%D8%B7%D9%81%D9%89_%D8%A5%D8%B3%D9%85%D8%A7%D8%B9%D9%8A%D9%84_-_%D9%85%D8%B5%D8%B1%29.mp3", null),
        Muezzin("banna", "محمود علي البنا", "Mahmoud Ali Al-Banna", "مصر", "Egypt", Region.EGYPT, "https://raw.githubusercontent.com/Kiwifu/adhan-mp3/main/Mahmoud_Ali_Al_Banna_-_Cairo_%28%D9%85%D8%AD%D9%85%D9%88%D8%AF_%D8%B9%D9%84%D9%8A_%D8%A7%D9%84%D8%A8%D9%86%D8%A7_-_%D8%A7%D9%84%D9%82%D8%A7%D9%87%D8%B1%D8%A9%29.mp3", null),
        Muezzin("tablawi", "محمد محمود الطبلاوي", "Mohamed Mahmoud Al-Tablawi", "مصر", "Egypt", Region.EGYPT, "https://raw.githubusercontent.com/bilalelsayed97/adhan_sounds/main/muhammed_altablawy.mp3", null),
        Muezzin("shuaisha", "أبو العينين شعيشع", "Abul Ainain Shuaisha", "مصر", "Egypt", Region.EGYPT, "https://raw.githubusercontent.com/Kiwifu/adhan-mp3/main/Abul_Ainain_Shuaisha_-_Cairo_%28%D8%A3%D8%A8%D9%88_%D8%A7%D9%84%D8%B9%D9%8A%D9%86%D9%8A%D9%86_%D8%B4%D8%B9%D9%8A%D8%B4%D8%B9_-_%D8%A7%D9%84%D9%82%D8%A7%D9%87%D8%B1%D8%A9%29.mp3", null),
        Muezzin("naqshbandi", "سيد النقشبندي", "Sayed Al-Naqshbandi", "مصر", "Egypt", Region.EGYPT, "https://raw.githubusercontent.com/Kiwifu/adhan-mp3/main/Sheikh_Al_Naqshbandi_%28%D8%A7%D9%84%D8%B4%D9%8A%D8%AE_%D8%A7%D9%84%D9%86%D9%82%D8%B4%D8%A8%D9%86%D8%AF%D9%89%29.mp3", null),
        Muezzin("toubar", "نصر الدين طوبار", "Nasr El-Din Tobar", "مصر", "Egypt", Region.EGYPT, "https://raw.githubusercontent.com/bilalelsayed97/adhan_sounds/main/nasr_eldeen_toubar.mp3", "https://raw.githubusercontent.com/bilalelsayed97/adhan_sounds/main/nasr_aldeen_tobar_fajr.mp3"),
        Muezzin("omran", "محمد عمران", "Mohamed Omran", "مصر", "Egypt", Region.EGYPT, "https://raw.githubusercontent.com/bilalelsayed97/adhan_sounds/main/mohamed_omran.mp3", "https://raw.githubusercontent.com/bilalelsayed97/adhan_sounds/main/mohamed_omran_fajr.mp3"),
        Muezzin("nuinaa", "أحمد نعينع", "Ahmed Nuinaa", "مصر", "Egypt", Region.EGYPT, "https://raw.githubusercontent.com/Kiwifu/adhan-mp3/main/Ahmed_Nuinaa_1_-_Egypt_%28%D8%A3%D8%AD%D9%85%D8%AF_%D9%86%D8%B9%D9%8A%D9%86%D8%B9_-_%D9%85%D8%B5%D8%B1%29.mp3", null),
        Muezzin("ali_mahmoud", "علي محمود", "Ali Mahmoud", "مصر", "Egypt", Region.EGYPT, "https://raw.githubusercontent.com/bilalelsayed97/adhan_sounds/main/aly_mahmoud.mp3", null),
        Muezzin("ghalwash", "راغب مصطفى غلوش", "Ragheb Mustafa Ghalwash", "مصر", "Egypt", Region.EGYPT, "https://raw.githubusercontent.com/bilalelsayed97/adhan_sounds/main/ragheb_ghalosh.mp3", null),
        Muezzin("sayad", "شعبان الصياد", "Shaaban Al-Sayad", "مصر", "Egypt", Region.EGYPT, "https://raw.githubusercontent.com/bilalelsayed97/adhan_sounds/main/shaaban_alsayad.mp3", null),
        Muezzin("fashni", "طه الفشني", "Taha Al-Fashni", "مصر", "Egypt", Region.EGYPT, "https://raw.githubusercontent.com/bilalelsayed97/adhan_sounds/main/taha_alfashn_1.mp3", null),
        Muezzin("bahtimi", "كامل يوسف البهتيمي", "Kamel Youssef Al-Bahtimi", "مصر", "Egypt", Region.EGYPT, "https://raw.githubusercontent.com/bilalelsayed97/adhan_sounds/main/kamel_youssef_albahtemy.mp3", null),
        Muezzin("zaher", "عبد العظيم زاهر", "Abdel Azim Zaher", "مصر", "Egypt", Region.EGYPT, "https://raw.githubusercontent.com/bilalelsayed97/adhan_sounds/main/abdel_azem_zaher.mp3", null),
        Muezzin("harak", "عبد الناصر حرك", "Abdel Nasser Harak", "مصر", "Egypt", Region.EGYPT, "https://raw.githubusercontent.com/bilalelsayed97/adhan_sounds/main/abdel_nasser_hark.mp3", "https://raw.githubusercontent.com/bilalelsayed97/adhan_sounds/main/fajr_abdel_nasser_harak.mp3"),
        Muezzin("tawakh", "محمود الطوخي", "Mahmoud Al-Tukhi", "مسجد الرفاعي، القاهرة", "Al-Rifai Mosque, Cairo", Region.EGYPT, "https://raw.githubusercontent.com/Kiwifu/adhan-mp3/main/Mahmud_Al_Tawakh_2_-_Masjid_Al_Rifai_Cairo_%28%D9%85%D8%AD%D9%85%D9%88%D8%AF_%D8%A7%D9%84%D8%B7%D9%88%D8%A7%D8%AE_-_%D9%85%D8%B3%D8%AC%D8%AF_%D8%A7%D9%84%D8%B1%D9%81%D8%A7%D8%B9%D9%8A_%D8%A7%D9%84%D9%82%D8%A7%D9%87%D8%B1%D8%A9%29.mp3", null),
        Muezzin("islam_sobhi", "إسلام صبحي", "Islam Sobhi", "مصر", "Egypt", Region.EGYPT, "https://raw.githubusercontent.com/bilalelsayed97/adhan_sounds/main/islam_sobhey.mp3", null),
        Muezzin("cairo", "أذان القاهرة", "Cairo adhan", "القاهرة", "Cairo", Region.EGYPT, "https://raw.githubusercontent.com/bilalelsayed97/adhan_sounds/main/adhan_egypt.mp3", "https://raw.githubusercontent.com/Kiwifu/adhan-mp3/main/Adhan_Fajr_Cairo_Egypt_%28%D8%A3%D8%B0%D8%A7%D9%86_%D8%A7%D9%84%D9%81%D8%AC%D8%B1_%D8%A7%D9%84%D9%82%D8%A7%D9%87%D8%B1%D8%A9_%D9%85%D8%B5%D8%B1%29.mp3"),
        Muezzin("makkah", "أذان الحرم المكي", "Masjid al-Haram adhan", "مكة المكرمة", "Makkah", Region.HARAMAIN, "https://raw.githubusercontent.com/bilalelsayed97/adhan_sounds/main/adhan_makkah.mp3", "https://raw.githubusercontent.com/Kiwifu/adhan-mp3/main/Adhan_Fajr_Al_Haram_Al_Maki_%28%D8%A3%D8%B0%D8%A7%D9%86_%D8%A7%D9%84%D9%81%D8%AC%D8%B1_%D8%A7%D9%84%D8%AD%D8%B1%D9%85_%D8%A7%D9%84%D9%85%D9%83%D9%8A%29.mp3"),
        Muezzin("madinah", "أذان المسجد النبوي", "Masjid an-Nabawi adhan", "المدينة المنورة", "Madinah", Region.HARAMAIN, "https://raw.githubusercontent.com/Kiwifu/adhan-mp3/main/Adhan_Al_Haram_Al_Madani_-_Al_Madinah_1_%28%D8%A3%D8%B0%D8%A7%D9%86_%D8%A7%D9%84%D8%AD%D8%B1%D9%85_%D8%A7%D9%84%D9%85%D8%AF%D9%86%D9%8A_-_%D8%A7%D9%84%D9%85%D8%AF%D9%8A%D9%86%D8%A9_%D8%A7%D9%84%D9%85%D9%86%D9%88%D8%B1%D8%A9%29.mp3", "https://raw.githubusercontent.com/Kiwifu/adhan-mp3/main/Adhan_Fajr_Al_Haram_Al_Madani_%28%D8%A3%D8%B0%D8%A7%D9%86_%D8%A7%D9%84%D9%81%D8%AC%D8%B1_%D8%A7%D9%84%D8%AD%D8%B1%D9%85_%D8%A7%D9%84%D9%85%D8%AF%D9%86%D9%8A%29.mp3"),
        Muezzin("mulla", "علي أحمد ملا", "Ali Ahmed Mulla", "الحرم المكي", "Masjid al-Haram", Region.HARAMAIN, "https://raw.githubusercontent.com/Kiwifu/adhan-mp3/main/Ali_Ibn_Ahmad_Mala_1_-_Al_Haram_Al_Maki_%28%D8%B9%D9%84%D9%8A_%D8%A8%D9%86_%D8%A3%D8%AD%D9%85%D8%AF_%D9%85%D9%84%D8%A7_-_%D8%A7%D9%84%D8%AD%D8%B1%D9%85_%D8%A7%D9%84%D9%85%D9%83%D9%8A%29.mp3", null),
        Muezzin("hadrawi", "فاروق حضراوي", "Farouk Hadrawi", "الحرم المكي", "Masjid al-Haram", Region.HARAMAIN, "https://raw.githubusercontent.com/Kiwifu/adhan-mp3/main/Faruq_Abdul_Rahman_Hadrawe_-_Al_Haram_Al_Maki_%28%D9%81%D8%A7%D8%B1%D9%88%D9%82_%D8%B9%D8%A8%D8%AF%D8%A7%D9%84%D8%B1%D8%AD%D9%85%D9%86_%D8%AD%D8%B6%D8%B1%D8%A7%D9%88%D9%8A_-_%D8%A7%D9%84%D8%AD%D8%B1%D9%85_%D8%A7%D9%84%D9%85%D9%83%D9%8A%29.mp3", null),
        Muezzin("bukhari", "عصام بخاري", "Essam Bukhari", "الحرم المكي", "Masjid al-Haram", Region.HARAMAIN, "https://raw.githubusercontent.com/Kiwifu/adhan-mp3/main/Assem_Bukhrare_-_Al_Haram_Al_Maki_%28%D8%B9%D8%A7%D8%B5%D9%85_%D8%A8%D8%AE%D8%B1%D8%A7%D8%B1%D9%8A_-_%D8%A7%D9%84%D8%AD%D8%B1%D9%85_%D8%A7%D9%84%D9%85%D9%83%D9%8A%29.mp3", null),
        Muezzin("nayef_fida", "نايف فيدة", "Nayef Fida", "الحرم المكي", "Masjid al-Haram", Region.HARAMAIN, "https://raw.githubusercontent.com/Kiwifu/adhan-mp3/main/Nayf_Fedah_-_Al_Haram_Al_Maki_%28%D9%86%D8%A7%D9%8A%D9%81_%D9%81%D8%AF%D8%A7%D8%AD_-_%D8%A7%D9%84%D8%AD%D8%B1%D9%85_%D8%A7%D9%84%D9%85%D9%83%D9%8A%29.mp3", null),
        Muezzin("surehi", "عبد المجيد السريحي", "Abdulmajeed Al-Surehi", "المسجد النبوي", "Masjid an-Nabawi", Region.HARAMAIN, "https://raw.githubusercontent.com/Kiwifu/adhan-mp3/main/Abdul_Majid_Al_Surehi_1_%28%D8%B9%D8%A8%D8%AF%D8%A7%D9%84%D9%85%D8%AC%D9%8A%D8%AF_%D8%A7%D9%84%D8%B3%D8%B1%D9%8A%D8%AD%D9%8A%29.mp3", null),
        Muezzin("aqsa", "أذان المسجد الأقصى", "Al-Aqsa adhan", "القدس", "Jerusalem", Region.HARAMAIN, "https://raw.githubusercontent.com/Kiwifu/adhan-mp3/main/Adhan_Al_Aqsa_-_Jerusalem_%28%D8%A3%D8%B0%D8%A7%D9%86_%D8%A7%D9%84%D9%85%D8%B3%D8%AC%D8%AF_%D8%A7%D9%84%D8%A3%D9%82%D8%B5%D9%89_-_%D8%A7%D9%84%D9%82%D8%AF%D8%B3%29.mp3", null),
        Muezzin("qazzaz", "ناجي قزاز", "Naji Qazzaz", "المسجد الأقصى", "Al-Aqsa Mosque", Region.HARAMAIN, "https://raw.githubusercontent.com/Kiwifu/adhan-mp3/main/Najee_Qazaz_-_Al_Aqsa_Jerusalem_%28%D9%86%D8%A7%D8%AC%D9%8A_%D9%82%D8%B2%D8%A7%D8%B2_-_%D8%A7%D9%84%D9%85%D8%B3%D8%AC%D8%AF_%D8%A7%D9%84%D8%A3%D9%82%D8%B5%D9%89_%D8%A7%D9%84%D9%82%D8%AF%D8%B3%29.mp3", null),
        Muezzin("qatami", "ناصر القطامي", "Nasser Al-Qatami", "السعودية", "Saudi Arabia", Region.GULF, "https://raw.githubusercontent.com/Kiwifu/adhan-mp3/main/Nasser_Al_Qatami_1_-_Saudi_Arabia_%28%D9%86%D8%A7%D8%B5%D8%B1_%D8%A7%D9%84%D9%82%D8%B7%D8%A7%D9%85%D9%8A_-_%D8%A7%D9%84%D9%85%D9%85%D9%84%D9%83%D8%A9_%D8%A7%D9%84%D8%B9%D8%B1%D8%A8%D9%8A%D8%A9_%D8%A7%D9%84%D8%B3%D8%B9%D9%88%D8%AF%D9%8A%D8%A9%29.mp3", null),
        Muezzin("zahrani", "منصور الزهراني", "Mansour Az-Zahrani", "السعودية", "Saudi Arabia", Region.GULF, "https://raw.githubusercontent.com/Kiwifu/adhan-mp3/main/Mansur_Al_Zahrane_%28%D9%85%D9%86%D8%B5%D9%88%D8%B1_%D8%A7%D9%84%D8%B2%D9%87%D8%B1%D8%A7%D9%86%D9%8A%29.mp3", null),
        Muezzin("dosari", "ياسر الدوسري", "Yasser Al-Dosari", "السعودية", "Saudi Arabia", Region.GULF, "https://raw.githubusercontent.com/bilalelsayed97/adhan_sounds/main/yasser_aldosary.mp3", "https://raw.githubusercontent.com/bilalelsayed97/adhan_sounds/main/yasser_aldosary_fajr.mp3"),
        Muezzin("abkar", "إدريس أبكر", "Idris Abkar", "السعودية", "Saudi Arabia", Region.GULF, "https://raw.githubusercontent.com/bilalelsayed97/adhan_sounds/main/edres_abkar.mp3", null),
        Muezzin("ghamdi", "سعد الغامدي", "Saad Al-Ghamdi", "السعودية", "Saudi Arabia", Region.GULF, "https://raw.githubusercontent.com/bilalelsayed97/adhan_sounds/main/saad_alghamdy.mp3", null),
        Muezzin("ajmi", "أحمد بن علي العجمي", "Ahmed Al-Ajmi", "السعودية", "Saudi Arabia", Region.GULF, "https://raw.githubusercontent.com/bilalelsayed97/adhan_sounds/main/ahmed_alagamy.mp3", null),
        Muezzin("nufais", "أحمد النفيس", "Ahmed Al-Nufais", "الكويت", "Kuwait", Region.GULF, "https://raw.githubusercontent.com/Kiwifu/adhan-mp3/main/Ahmad_Al_Nafees_%28%D8%A3%D8%AD%D9%85%D8%AF_%D8%A7%D9%84%D9%86%D9%81%D9%8A%D8%B3%29.mp3", null),
        Muezzin("abbad", "فارس عباد", "Fares Abbad", "السعودية", "Saudi Arabia", Region.GULF, "https://raw.githubusercontent.com/bilalelsayed97/adhan_sounds/main/fares_abbad.mp3", null),
        Muezzin("baloushi", "هزاع البلوشي", "Hazza Al-Balushi", "الإمارات", "UAE", Region.GULF, "https://raw.githubusercontent.com/bilalelsayed97/adhan_sounds/main/hazzaa_albaloshy.mp3", null),
        Muezzin("deghreri", "حمد الدغريري", "Hamad Al-Deghreri", "السعودية", "Saudi Arabia", Region.GULF, "https://raw.githubusercontent.com/Kiwifu/adhan-mp3/main/Hamad_Al_Daghriri_%28%D8%AD%D9%85%D8%AF_%D8%A7%D9%84%D8%AF%D8%BA%D8%B1%D9%8A%D8%B1%D9%8A%29.mp3", null),
        Muezzin("malki", "حمدان المالكي", "Hamdan Al-Malki", "نجران، السعودية", "Najran, Saudi Arabia", Region.GULF, "https://raw.githubusercontent.com/Kiwifu/adhan-mp3/main/Hamdan_Al_Maleke_-_Najran_Saudi_Arabia_%28%D8%AD%D9%85%D8%AF%D8%A7%D9%86_%D8%A7%D9%84%D9%85%D9%84%D9%83%D9%8A_-_%D9%86%D8%AC%D8%B1%D8%A7%D9%86_%D8%A7%D9%84%D8%B3%D8%B9%D9%88%D8%AF%D9%8A%D8%A9%29.mp3", null),
        Muezzin("hamathani", "ماجد الهمداني", "Majed Al-Hamathani", "السعودية", "Saudi Arabia", Region.GULF, "https://raw.githubusercontent.com/abodehq/Athan-MP3/master/Sounds/Athan%20Majed%20Al-hamathani.mp3", null),
        Muezzin("arkani", "إبراهيم الأركاني", "Ibrahim Al-Arkani", "السعودية", "Saudi Arabia", Region.GULF, "https://raw.githubusercontent.com/abodehq/Athan-MP3/master/Sounds/Athan%20Ibrahim%20Al-Arkani.mp3", null),
        Muezzin("yamani", "وديع اليمني", "Wadee Al-Yamani", "السعودية", "Saudi Arabia", Region.GULF, "https://raw.githubusercontent.com/Kiwifu/adhan-mp3/main/Wadi_Hamad_Al-Yamani_%28%D9%88%D8%A7%D8%AF%D9%8A_%D8%AD%D9%85%D8%AF_%D8%A7%D9%84%D9%8A%D9%85%D8%A7%D9%86%D9%8A%29.mp3", null),
        Muezzin("jehaim", "خالد الجهيم", "Khaled Al-Juhaim", "السعودية", "Saudi Arabia", Region.GULF, null, "https://raw.githubusercontent.com/bilalelsayed97/adhan_sounds/main/fajr_khaled_aljohaim.mp3"),
        Muezzin("tarablsi", "أحمد الطرابلسي", "Ahmed Al-Tarabulsi", "الكويت", "Kuwait", Region.GULF, "https://raw.githubusercontent.com/Kiwifu/adhan-mp3/main/Ahmd_At-Trablsy_1_%28%D8%A3%D8%AD%D9%85%D8%AF_%D8%A7%D9%84%D8%B7%D8%B1%D8%A7%D8%A8%D9%84%D8%B3%D9%8A%29.mp3", "https://raw.githubusercontent.com/Kiwifu/adhan-mp3/main/Ahmd_At-Trablsy_-_Fajr_Kuwait_%28%D8%A3%D8%AD%D9%85%D8%AF_%D8%A7%D9%84%D8%B7%D8%B1%D8%A7%D8%A8%D9%84%D8%B3%D9%8A_-_%D9%81%D8%AC%D8%B1_%D8%A7%D9%84%D9%83%D9%88%D9%8A%D8%AA%29.mp3"),
        Muezzin("mejlad", "أحمد نواف المجلاد", "Ahmad Nawaf Al-Mejlad", "الكويت", "Kuwait", Region.GULF, "https://raw.githubusercontent.com/Kiwifu/adhan-mp3/main/Ahmad_Nawaf_Al_Mejlad_1_%28%D8%A3%D8%AD%D9%85%D8%AF_%D9%86%D9%88%D8%A7%D9%81_%D8%A7%D9%84%D9%85%D8%AC%D9%84%D8%AF%29.mp3", "https://raw.githubusercontent.com/Kiwifu/adhan-mp3/main/Ahmad_Nawaf_Al_Mejlad_2_-_Fajr_%28%D8%A3%D8%AD%D9%85%D8%AF_%D9%86%D9%88%D8%A7%D9%81_%D8%A7%D9%84%D9%85%D8%AC%D9%84%D8%AF_-_%D8%A7%D9%84%D9%81%D8%AC%D8%B1%29.mp3"),
        Muezzin("kuwait", "أذان الكويت", "Kuwait adhan", "الكويت", "Kuwait", Region.GULF, "https://raw.githubusercontent.com/Kiwifu/adhan-mp3/main/Adhan_Kuwait_%28%D8%A3%D8%B0%D8%A7%D9%86_%D8%A7%D9%84%D9%83%D9%88%D9%8A%D8%AA%29.mp3", "https://raw.githubusercontent.com/Kiwifu/adhan-mp3/main/Adhan_Fajr_Kuwait_1_%28%D8%A3%D8%B0%D8%A7%D9%86_%D8%A7%D9%84%D9%81%D8%AC%D8%B1_%D8%A7%D9%84%D9%83%D9%88%D9%8A%D8%AA%29.mp3"),
        Muezzin("dubai", "أذان دبي", "Dubai adhan", "الإمارات", "UAE", Region.GULF, "https://raw.githubusercontent.com/Kiwifu/adhan-mp3/main/Adhan_Dubai_UAE_%28%D8%A3%D8%B0%D8%A7%D9%86_%D8%AF%D8%A8%D9%8A_%D8%A7%D9%84%D8%A5%D9%85%D8%A7%D8%B1%D8%A7%D8%AA%29.mp3", null),
        Muezzin("emadi", "أحمد العمادي", "Ahmad Al-Emadi", "قطر", "Qatar", Region.GULF, "https://raw.githubusercontent.com/Kiwifu/adhan-mp3/main/Ahmad_Al_Amade_1_-_Qatar_%28%D8%A3%D8%AD%D9%85%D8%AF_%D8%A7%D9%84%D8%B9%D9%85%D8%A7%D8%AF%D9%8A_-_%D9%82%D8%B7%D8%B1%29.mp3", null),
        Muezzin("yaseen", "ياسين العساف", "Yaseen Al-Assaf", "العراق", "Iraq", Region.OTHER, "https://raw.githubusercontent.com/Kiwifu/adhan-mp3/main/Yaseen_Al_Aassaf_1_%28%D9%8A%D8%A7%D8%B3%D9%8A%D9%86_%D8%A7%D9%84%D8%A3%D8%B3%D9%81%29.mp3", "https://raw.githubusercontent.com/Kiwifu/adhan-mp3/main/Yaseen_Al_Aassaf_2_-_Fajr_Iraq_%28%D9%8A%D8%A7%D8%B3%D9%8A%D9%86_%D8%A7%D9%84%D8%A3%D8%B3%D9%81_-_%D9%81%D8%AC%D8%B1_%D8%A7%D9%84%D8%B9%D8%B1%D8%A7%D9%82%29.mp3"),
        Muezzin("jabr", "إبراهيم جبر أبو رهق", "Ibrahim Jabr Abu Rahaq", "", "", Region.OTHER, "https://raw.githubusercontent.com/Kiwifu/adhan-mp3/main/Ibraheem_Jabr_Abu_Raheq_%28%D8%A5%D8%A8%D8%B1%D8%A7%D9%87%D9%8A%D9%85_%D8%AC%D8%A8%D8%B1_%D8%A3%D8%A8%D9%88_%D8%B1%D8%AD%D9%8A%D9%82%29.mp3", "https://raw.githubusercontent.com/Kiwifu/adhan-mp3/main/Ibraheem_Jabr_Abu_Raheq_-_Fajr_%28%D8%A5%D8%A8%D8%B1%D8%A7%D9%87%D9%8A%D9%85_%D8%AC%D8%A8%D8%B1_%D8%A3%D8%A8%D9%88_%D8%B1%D8%AD%D9%8A%D9%82_-_%D8%A7%D9%84%D9%81%D8%AC%D8%B1%29.mp3"),
        Muezzin("algeria", "أذان الجزائر", "Algeria adhan", "الجزائر", "Algeria", Region.OTHER, "https://raw.githubusercontent.com/Kiwifu/adhan-mp3/main/Riad_Al_Djazairi_-_Algeria_%28%D8%B1%D9%8A%D8%A7%D8%B6_%D8%A7%D9%84%D8%AC%D8%B2%D8%A7%D8%A6%D8%B1%D9%8A_-_%D8%A7%D9%84%D8%AC%D8%B2%D8%A7%D8%A6%D8%B1%29.mp3", "https://raw.githubusercontent.com/Kiwifu/adhan-mp3/main/Adhan_Fajr_Algeria_%28%D8%A3%D8%B0%D8%A7%D9%86_%D8%A7%D9%84%D9%81%D8%AC%D8%B1_%D8%A7%D9%84%D8%AC%D8%B2%D8%A7%D8%A6%D8%B1%29.mp3"),
        Muezzin("morocco", "كمال المروش", "Kamal Al-Marush", "المغرب", "Morocco", Region.OTHER, "https://raw.githubusercontent.com/Kiwifu/adhan-mp3/main/Kamal_Muhammad_Al_Marush_2_-_Morocco_%28%D9%83%D9%85%D8%A7%D9%84_%D9%85%D8%AD%D9%85%D8%AF_%D8%A7%D9%84%D9%85%D8%B1%D8%B9%D9%88%D8%B4_-_%D8%A7%D9%84%D9%85%D8%BA%D8%B1%D8%A8%29.mp3", null),
        Muezzin("syria", "أذان سوريا", "Syria adhan", "سوريا", "Syria", Region.OTHER, "https://raw.githubusercontent.com/Kiwifu/adhan-mp3/main/Adhan_Syria_%28%D8%A3%D8%B0%D8%A7%D9%86_%D8%B3%D9%88%D8%B1%D9%8A%D8%A7%29.mp3", null),
        Muezzin("turkey", "أذان تركيا", "Turkey adhan", "تركيا", "Turkey", Region.OTHER, "https://raw.githubusercontent.com/Kiwifu/adhan-mp3/main/Adhan_Turkey_1_%28%D8%A3%D8%B0%D8%A7%D9%86_%D8%AA%D8%B1%D9%83%D9%8A%D8%A7%29.mp3", null),
        Muezzin("chebae", "مالك شيبة", "Malek Chebae", "", "", Region.OTHER, null, "https://raw.githubusercontent.com/abodehq/Athan-MP3/master/Sounds/Athan%20Al-fajer%20-%20Malek%20chebae.mp3"),
    )

    private val byIdMap = builtIn.associateBy { it.id }

    fun find(id: String?): Muezzin? = id?.let { i -> byIdMap[i] ?: CustomMuezzins.list.firstOrNull { it.id == i } }
    fun byId(id: String?): Muezzin = find(id) ?: builtIn.first()

    val DEFAULTS: Map<Prayer, String> = mapOf(
        Prayer.FAJR to "basit",
        Prayer.DHUHR to "minshawi",
        Prayer.ASR to "refaat",
        Prayer.MAGHRIB to "qatami",
        Prayer.ISHA to "abkar",
    )

    /** The muezzin for a prayer, falling back to the default if the saved one can't call it. */
    fun forPrayer(p: Prayer, savedId: String?): Muezzin {
        val m = find(savedId)
        return if (m != null && m.canCall(p)) m else byId(DEFAULTS[p])
    }
}
