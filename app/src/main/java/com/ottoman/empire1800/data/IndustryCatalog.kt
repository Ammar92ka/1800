package com.ottoman.empire1800.data

val resourceLabels: Map<String, String> = mapOf(
    "grain" to "الحبوب",
    "cotton" to "القطن",
    "silk" to "الحرير",
    "wool" to "الصوف",
    "coal" to "الفحم",
    "minerals" to "المعادن",
    "timber" to "الأخشاب",
    "port" to "ميناء تجاري",
    "coffee" to "البن",
    "sugar" to "القصب والسكر",
    "religious" to "مقامات دينية",
    "oil" to "النفط",
)

data class FactoryDef(
    val key: String,
    val name: String,
    val shortDesc: String,
    val costShare: Double,
    val baseYieldShare: Double,
    val revenueLine: String,
    val bonusResources: List<String>,
    val requiresResources: List<String>,
)

val factoryDefs: List<FactoryDef> = listOf(
    FactoryDef(
        key = "paper_mill",
        name = "مصنع ورق ومطابع",
        shortDesc = "يخفض مصاريف الإدارة (-5% لكل مستوى حتى -25%) ويرفع محو الأمية.",
        costShare = 0.045,
        baseYieldShare = 0.008,
        revenueLine = "other",
        bonusResources = listOf("timber"),
        requiresResources = emptyList(),
    ),
    FactoryDef(
        key = "textile",
        name = "مصنع نسيج وحرير",
        shortDesc = "ينتج أقمشة ويدر إيرادا صناعيا؛ يتضاعف إنتاجه مع القطن والحرير والصوف.",
        costShare = 0.055,
        baseYieldShare = 0.015,
        revenueLine = "other",
        bonusResources = listOf("cotton", "wool", "silk"),
        requiresResources = emptyList(),
    ),
    FactoryDef(
        key = "coal_mine",
        name = "منجم فحم ومعادن",
        shortDesc = "يمد مصانع الدولة بالطاقة (+25% لإنتاج كل المصانع) ويدر عائدا صناعيا.",
        costShare = 0.060,
        baseYieldShare = 0.014,
        revenueLine = "other",
        bonusResources = listOf("coal", "oil"),
        requiresResources = listOf("coal", "minerals", "oil"),
    ),
    FactoryDef(
        key = "arms_factory",
        name = "مصنع سلاح وذخيرة",
        shortDesc = "يرفع قوة الجيش في الحرب (+2.5%/مستوى حتى +15%) ويخفض كلفة الحرب (-3.5%/مستوى حتى -20%).",
        costShare = 0.065,
        baseYieldShare = 0.006,
        revenueLine = "other",
        bonusResources = listOf("minerals", "coal"),
        requiresResources = emptyList(),
    ),
    FactoryDef(
        key = "food_mill",
        name = "معمل غذاء ومطاحن",
        shortDesc = "يخفف صدمات أسعار الحبوب ويرفع رضا الولاية واستقرارها.",
        costShare = 0.040,
        baseYieldShare = 0.011,
        revenueLine = "other",
        bonusResources = listOf("grain", "sugar", "coffee"),
        requiresResources = emptyList(),
    ),
    FactoryDef(
        key = "trade_depot",
        name = "رصيف وتجارة",
        shortDesc = "يبنى في الموانئ لرفع عوائد الجمارك والتبادل البحري.",
        costShare = 0.050,
        baseYieldShare = 0.016,
        revenueLine = "customs",
        bonusResources = listOf("port"),
        requiresResources = listOf("port"),
    ),
)

fun factoryDef(key: String): FactoryDef? = factoryDefs.firstOrNull { it.key == key }

data class EconomicSystemDef(
    val key: String,
    val name: String,
    val subtitle: String,
    val bonus: String,
    val tradeoff: String,
    val canStateBuild: Boolean,
    val canSubsidize: Boolean,
    val buildCostMult: Double,
    val yieldMult: Double,
    val tariffMult: Double,
)

val economicSystemDefs: List<EconomicSystemDef> = listOf(
    EconomicSystemDef(
        key = "state_capitalism",
        name = "رأسمالية الدولة والميري",
        subtitle = "السلطنة تخطط وتبني المصانع وتدير الاحتكارات",
        bonus = "الدولة تبني وترقي المصانع مباشرة بكلفة قياسية (1.0×)",
        tradeoff = "لا يبني التجار مصانع تلقائيا لحساب القطاع الخاص",
        canStateBuild = true,
        canSubsidize = false,
        buildCostMult = 1.0,
        yieldMult = 1.0,
        tariffMult = 1.0,
    ),
    EconomicSystemDef(
        key = "interventionism",
        name = "الاقتصاد المختلط والتدخل",
        subtitle = "شراكة بين الخزينة والتجار مع دعم الورش المتعثرة",
        bonus = "إنتاج +5%، دعم المصانع المتعثرة، واستثمار تلقائي للتجار عند رضاهم (≥60)",
        tradeoff = "كلفة بناء الدولة أعلى قليلا (+5%) وإعانات المصانع تكلف الخزينة",
        canStateBuild = true,
        canSubsidize = true,
        buildCostMult = 1.05,
        yieldMult = 1.05,
        tariffMult = 1.0,
    ),
    EconomicSystemDef(
        key = "laissez_faire",
        name = "السوق الحر وتشجيع التجار",
        subtitle = "حرية التجارة ورأس المال الخاص في الموانئ والمدن",
        bonus = "عائد المصانع +15%، يبني التجار المصانع مجانا، ويرتفع رضا التجار",
        tradeoff = "يمنع الدولة من بناء المصانع أو دعمها، وقد تغلق الورش الضعيفة وقت الأزمات",
        canStateBuild = false,
        canSubsidize = false,
        buildCostMult = 1.0,
        yieldMult = 1.15,
        tariffMult = 1.0,
    ),
    EconomicSystemDef(
        key = "protectionist",
        name = "الحماية الوطنية والتعريفات",
        subtitle = "حواجز جمركية لحماية الإنتاج العثماني الناشئ",
        bonus = "عوائد الجمارك +25% وإنتاج المصانع +25% مع حماية الورش من الإفلاس",
        tradeoff = "تتوتر العلاقات دوريا مع بريطانيا وفرنسا (-1/فترة و-4 عند التحول)",
        canStateBuild = true,
        canSubsidize = false,
        buildCostMult = 1.0,
        yieldMult = 1.25,
        tariffMult = 1.25,
    ),
)

fun economicSystemDef(key: String): EconomicSystemDef =
    economicSystemDefs.firstOrNull { it.key == key } ?: economicSystemDefs[0]

data class FrontierRegionDef(
    val key: String,
    val name: String,
    val weight: Double,
    val mood: Double,
    val sep: Double,
    val autonomy: Double,
    val conquerEnemies: List<String>,
    val requiresRegion: String? = null,
    val about: String,
)

val frontierRegionDefs: List<FrontierRegionDef> = listOf(
    FrontierRegionDef(
        key = "algeria",
        name = "الجزائر",
        weight = 0.07,
        mood = 58.0,
        sep = 0.45,
        autonomy = 18.0,
        conquerEnemies = listOf("france"),
        about = "ثغر مغربي بحري وزراعي؛ تُستعاد بالانتصار الحاسم على فرنسا.",
    ),
    FrontierRegionDef(
        key = "tunisia",
        name = "تونس",
        weight = 0.05,
        mood = 60.0,
        sep = 0.40,
        autonomy = 15.0,
        conquerEnemies = listOf("france"),
        requiresRegion = "algeria",
        about = "إيالة تونسية ذات موانئ وخيرات؛ تُفتح بالانتصار الحاسم على فرنسا بعد الجزائر.",
    ),
    FrontierRegionDef(
        key = "caucasus",
        name = "القوقاز",
        weight = 0.08,
        mood = 52.0,
        sep = 0.55,
        autonomy = 22.0,
        conquerEnemies = listOf("russia", "persia"),
        about = "ثغور قوقازية غنية بالفحم والمعادن ونفط باكو (1875+)؛ تُفتح بالانتصار الحاسم على روسيا أو إيران.",
    ),
    FrontierRegionDef(
        key = "crete",
        name = "كريت",
        weight = 0.04,
        mood = 54.0,
        sep = 0.50,
        autonomy = 20.0,
        conquerEnemies = listOf("britain", "austria", "germany"),
        about = "جزيرة استراتيجية في شرق المتوسط؛ تُفتح بالانتصار الحاسم على بريطانيا أو النمسا أو ألمانيا.",
    ),
)

fun frontierRegionDef(key: String): FrontierRegionDef? = frontierRegionDefs.firstOrNull { it.key == key }
fun isFrontierRegionKey(key: String): Boolean = frontierRegionDef(key) != null

data class SubProvinceDef(
    val key: String,
    val parentKey: String,
    val name: String,
    val resources: List<String>,
    val slots: Int,
    val oilFromYear: Int? = null,
)

val subProvinceDefs: List<SubProvinceDef> = listOf(
    // الأناضول
    SubProvinceDef("anatolia_istanbul", "anatolia", "إسطنبول وبورصة", listOf("silk", "port"), 3),
    SubProvinceDef("anatolia_izmir", "anatolia", "إزمير", listOf("cotton", "port"), 3),
    SubProvinceDef("anatolia_trabzon", "anatolia", "طرابزون وزونغولداق", listOf("coal", "timber"), 3),
    // الروملي والبلقان
    SubProvinceDef("rumelia_salonica", "rumelia", "سلانيك", listOf("port", "grain"), 3),
    SubProvinceDef("rumelia_sofia", "rumelia", "صوفيا", listOf("coal", "minerals"), 2),
    SubProvinceDef("rumelia_bosnia", "rumelia", "البوسنة", listOf("timber", "minerals"), 2),
    // مصر
    SubProvinceDef("egypt_cairo", "egypt", "القاهرة والدلتا", listOf("cotton", "grain"), 3),
    SubProvinceDef("egypt_alexandria", "egypt", "الإسكندرية", listOf("port", "cotton"), 3),
    SubProvinceDef("egypt_upper", "egypt", "الصعيد", listOf("sugar", "grain"), 2),
    // الشام
    SubProvinceDef("syria_damascus", "syria", "دمشق", listOf("grain", "silk"), 3),
    SubProvinceDef("syria_aleppo", "syria", "حلب", listOf("cotton", "wool"), 2),
    SubProvinceDef("syria_beirut", "syria", "بيروت والقدس", listOf("port", "grain"), 2),
    // العراق
    SubProvinceDef("iraq_baghdad", "iraq", "بغداد", listOf("grain", "wool"), 3),
    SubProvinceDef("iraq_basra", "iraq", "البصرة", listOf("port", "grain"), 2),
    SubProvinceDef("iraq_mosul", "iraq", "الموصل", listOf("minerals", "grain"), 2, oilFromYear = 1890),
    // الحجاز واليمن
    SubProvinceDef("hijaz_mecca", "hijaz", "مكة والمدينة", listOf("religious"), 2),
    SubProvinceDef("hijaz_jeddah", "hijaz", "جدة", listOf("port"), 2),
    SubProvinceDef("hijaz_yemen", "hijaz", "اليمن", listOf("coffee", "grain"), 2),
    // الثغور القابلة للفتح
    SubProvinceDef("algeria_algiers", "algeria", "الجزائر", listOf("port", "grain"), 3),
    SubProvinceDef("algeria_constantine", "algeria", "قسنطينة", listOf("minerals", "timber"), 2),
    SubProvinceDef("tunisia_tunis", "tunisia", "تونس", listOf("port", "grain"), 2),
    SubProvinceDef("tunisia_sfax", "tunisia", "صفاقس", listOf("minerals", "wool"), 2),
    SubProvinceDef("caucasus_baku", "caucasus", "باكو وشيروان", listOf("coal", "minerals"), 3, oilFromYear = 1875),
    SubProvinceDef("caucasus_kars", "caucasus", "قارص وباطوم", listOf("timber", "port"), 2),
    SubProvinceDef("crete_heraklion", "crete", "كاندية (هيراكليون)", listOf("port", "grain", "timber"), 2),
)

fun subProvinceDef(key: String): SubProvinceDef? = subProvinceDefs.firstOrNull { it.key == key }
fun subProvincesForParent(parentKey: String): List<SubProvinceDef> = subProvinceDefs.filter { it.parentKey == parentKey }

fun effectiveSubProvinceResources(
    def: SubProvinceDef,
    year: Int,
    oilDiscovered: List<String> = emptyList(),
): List<String> {
    val hasOil = (def.oilFromYear != null && year >= def.oilFromYear) || oilDiscovered.contains(def.key)
    return if (hasOil && !def.resources.contains("oil")) def.resources + "oil" else def.resources
}
