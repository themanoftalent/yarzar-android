package com.themanoftalent.yarzar

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class AdditiveEntry(
    val code: String,
    val name: String,
    val category: String,
    val risk: String,
    val concentrationTier: String,
    val summary: String,
    val isEndocrineDisruptor: Boolean = false
) {
    val penaltyPoints: Int
        get() = when (risk) {
            "High Risk" -> 22
            "Moderate Risk" -> 11
            "Low Risk" -> 4
            else -> 0
        }

    val doseMultiplier: Double
        get() = when {
            concentrationTier.startsWith("Trace") -> 0.55
            concentrationTier.startsWith("Minor") -> 0.85
            else -> 1.15
        }
}

data class ProductEntry(
    val id: String,
    val barcode: String,
    val name: String,
    val brand: String,
    val category: String,
    val domain: String,
    val emoji: String,
    val score: Int,
    val nutritionScore: Int,
    val additiveScore: Int,
    val organicScore: Int,
    val isOrganic: Boolean,
    val containsPalmOil: Boolean,
    val containsGluten: Boolean,
    val containsLactose: Boolean,
    val ingredients: String,
    val additives: List<AdditiveEntry>,
    val wholeFoodNote: String? = null,
    val healthierAltIds: List<String> = emptyList()
)

val Emerald = Color(0xFF10B981)
val MintTeal = Color(0xFF14B8A6)
val LimeGood = Color(0xFF22C55E)
val AmberMediocre = Color(0xFFF59E0B)
val CrimsonPoor = Color(0xFFEF4444)

fun scoreColor(score: Int): Color = when {
    score >= 75 -> Emerald
    score >= 50 -> LimeGood
    score >= 20 -> AmberMediocre
    else -> CrimsonPoor
}

fun ratingLabel(score: Int, lang: String): String = when {
    score >= 75 -> if (lang == "tr") "Mükemmel" else "Excellent"
    score >= 50 -> if (lang == "tr") "İyi" else "Good"
    score >= 20 -> if (lang == "tr") "Orta" else "Mediocre"
    else -> if (lang == "tr") "Zayıf" else "Poor"
}

fun recalculateProductEntry(product: ProductEntry): ProductEntry {
    val penalty = product.additives.sumOf { it.penaltyPoints * it.doseMultiplier }
    return if (product.domain == "Cosmetic") {
        val safety = max(4, min(100, (100.0 - penalty * 2.15).roundToInt()))
        val org = if (product.isOrganic) 10 else 0
        val total = min(100, safety + org)
        product.copy(score = total, additiveScore = safety, organicScore = org)
    } else {
        val addScore = max(0, min(30, (30.0 - penalty).roundToInt()))
        val orgScore = if (product.isOrganic) 10 else 0
        val total = max(0, min(100, product.nutritionScore + addScore + orgScore))
        product.copy(score = total, additiveScore = addScore, organicScore = orgScore)
    }
}

val SeedAdditives = listOf(
    AdditiveEntry("E250", "Sodium Nitrite", "Preservative", "High Risk", "Minor (0.5–2%)", "Forms nitrosamines; associated with elevated colorectal risk (WHO/IARC)."),
    AdditiveEntry("E320", "BHA", "Synthetic Antioxidant", "High Risk", "Trace (<0.5%)", "Suspected endocrine disruptor and potential carcinogen.", true),
    AdditiveEntry("E102", "Tartrazine", "Synthetic Dye", "High Risk", "Trace (<0.5%)", "Linked to hypersensitivity and hyperactivity in sensitive children."),
    AdditiveEntry("E150d", "Sulfite Ammonia Caramel", "Coloring", "Moderate Risk", "Minor (0.5–2%)", "Contains 4-MEI processing byproduct monitored by EFSA."),
    AdditiveEntry("INCI-PARABEN", "Propylparaben", "Cosmetic Preservative", "High Risk", "Minor (0.5–2%)", "Estrogenic endocrine-disrupting potential in leave-on cosmetics.", true),
    AdditiveEntry("E322", "Sunflower / Soy Lecithin", "Natural Emulsifier", "Risk-Free", "Trace (<0.5%)", "Safe phospholipid emulsifier with zero toxicity concern."),
    AdditiveEntry("INCI-NIACINAMIDE", "Niacinamide (Vitamin B3)", "Skin Barrier Active", "Risk-Free", "High (>2%)", "Strengthens ceramide lipid barrier and calms inflammation.")
)

object OpenFactsAndroidClient {
    private const val USER_AGENT = "Yarzar/1.6.3 (Android; https://github.com/themanoftalent/yarzar-android)"

    suspend fun lookupOrSearch(query: String): List<ProductEntry> = withContext(Dispatchers.IO) {
        val cleaned = query.trim()
        if (cleaned.isEmpty()) return@withContext emptyList()
        val digits = cleaned.filter { it.isDigit() }
        val isBarcode = digits.length >= 6 && digits.length == cleaned.replace("-", "").replace(" ", "").length

        if (isBarcode) {
            fetchByBarcode(digits)?.let { return@withContext listOf(it) }
            return@withContext emptyList()
        } else {
            return@withContext searchByName(cleaned)
        }
    }

    private fun fetchByBarcode(barcode: String): ProductEntry? {
        val urls = listOf(
            "https://world.openfoodfacts.org/api/v2/product/$barcode.json" to "Food",
            "https://world.openbeautyfacts.org/api/v2/product/$barcode.json" to "Cosmetic"
        )
        for ((urlStr, domain) in urls) {
            try {
                val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 8000
                    readTimeout = 8000
                    setRequestProperty("User-Agent", USER_AGENT)
                }
                if (conn.responseCode == 200) {
                    val body = conn.inputStream.bufferedReader().use { it.readText() }
                    val root = JSONObject(body)
                    if (root.optInt("status") == 1) {
                        val p = root.optJSONObject("product")
                        if (p != null) {
                            parseProductJson(p, barcode, domain)?.let { return it }
                        }
                    }
                }
            } catch (_: Exception) {
            }
        }
        return null
    }

    private fun searchByName(query: String): List<ProductEntry> {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val urlStr = "https://world.openfoodfacts.org/cgi/search.pl?search_terms=$encoded&search_simple=1&action=process&json=1&page_size=15"
        val results = mutableListOf<ProductEntry>()
        try {
            val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                connectTimeout = 9000
                readTimeout = 9000
                setRequestProperty("User-Agent", USER_AGENT)
            }
            if (conn.responseCode == 200) {
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                val root = JSONObject(body)
                val arr = root.optJSONArray("products") ?: return emptyList()
                for (i in 0 until arr.length()) {
                    val p = arr.optJSONObject(i) ?: continue
                    val code = p.optString("code", "").trim()
                    if (code.isNotEmpty()) {
                        parseProductJson(p, code, "Food")?.let { results.add(it) }
                    }
                }
            }
        } catch (_: Exception) {
        }
        return results
    }

    private fun parseProductJson(p: JSONObject, barcode: String, domain: String): ProductEntry? {
        val name = p.optString("product_name").ifEmpty {
            p.optString("product_name_tr").ifEmpty { p.optString("product_name_en") }
        }.trim()
        if (name.isEmpty()) return null

        val brand = p.optString("brands", "OpenFacts Verified").split(",").firstOrNull()?.trim() ?: "OpenFacts"
        val category = p.optString("categories", domain).split(",").firstOrNull()?.trim() ?: domain
        val ingredients = p.optString("ingredients_text").ifEmpty {
            p.optString("ingredients_text_tr").ifEmpty { "Ingredients fetched from OpenFacts." }
        }

        val labels = p.optJSONArray("labels_tags")
        var isOrganic = false
        if (labels != null) {
            for (i in 0 until labels.length()) {
                val tag = labels.optString(i).lowercase()
                if (tag.contains("organic") || tag.contains("bio") || tag.contains("organik")) {
                    isOrganic = true
                }
            }
        }

        val nut = p.optJSONObject("nutriments")
        val kcal = nut?.optDouble("energy-kcal_100g", 0.0)?.takeIf { !it.isNaN() } ?: 0.0
        val sugar = nut?.optDouble("sugars_100g", 0.0)?.takeIf { !it.isNaN() } ?: 0.0
        val satFat = nut?.optDouble("saturated-fat_100g", 0.0)?.takeIf { !it.isNaN() } ?: 0.0
        val salt = nut?.optDouble("salt_100g", 0.0)?.takeIf { !it.isNaN() } ?: 0.0
        val fiber = nut?.optDouble("fiber_100g", 0.0)?.takeIf { !it.isNaN() } ?: 0.0
        val protein = nut?.optDouble("proteins_100g", 0.0)?.takeIf { !it.isNaN() } ?: 0.0

        val matchedAdditives = mutableListOf<AdditiveEntry>()
        val lowerIng = ingredients.lowercase()
        SeedAdditives.forEach { ref ->
            if (lowerIng.contains(ref.code.lowercase()) || lowerIng.contains(ref.name.split(" ").first().lowercase())) {
                matchedAdditives.add(ref)
            }
        }
        val addTags = p.optJSONArray("additives_tags")
        if (addTags != null) {
            for (i in 0 until addTags.length()) {
                val code = addTags.optString(i).replace("en:", "").uppercase()
                val existing = SeedAdditives.firstOrNull { it.code.equals(code, ignoreCase = true) }
                if (existing != null) {
                    if (matchedAdditives.none { it.code == existing.code }) matchedAdditives.add(existing)
                } else if (code.startsWith("E") && matchedAdditives.none { it.code == code }) {
                    matchedAdditives.add(
                        AdditiveEntry(code, "Additive $code", "Food Additive", "Low Risk", "Trace (<0.5%)", "Detected in OpenFoodFacts label registry.")
                    )
                }
            }
        }

        var neg = 0.0
        neg += min(10.0, kcal / 80.0)
        neg += min(15.0, sugar / 3.0)
        neg += min(10.0, satFat / 1.2)
        neg += min(10.0, salt * 5.0)
        var pos = 0.0
        pos += min(8.0, fiber * 1.6)
        pos += min(8.0, protein * 0.9)

        val nutritionScore = max(0, min(60, (60.0 - neg * 1.35 + pos * 1.15).roundToInt()))
        val penalty = matchedAdditives.sumOf { it.penaltyPoints * it.doseMultiplier }
        val additiveScore = max(0, min(30, (30.0 - penalty).roundToInt()))
        val organicScore = if (isOrganic) 10 else 0
        val total = max(0, min(100, nutritionScore + additiveScore + organicScore))

        return ProductEntry(
            id = "off-$barcode",
            barcode = barcode,
            name = name,
            brand = brand,
            category = category,
            domain = domain,
            emoji = if (domain == "Cosmetic") "🧴" else "📦",
            score = total,
            nutritionScore = nutritionScore,
            additiveScore = additiveScore,
            organicScore = organicScore,
            isOrganic = isOrganic,
            containsPalmOil = lowerIng.contains("palm") || lowerIng.contains("palmiye"),
            containsGluten = lowerIng.contains("wheat") || lowerIng.contains("gluten") || lowerIng.contains("buğday"),
            containsLactose = lowerIng.contains("milk") || lowerIng.contains("whey") || lowerIng.contains("süt"),
            ingredients = ingredients,
            additives = matchedAdditives
        )
    }
}

val SeedCatalog = listOf(
    ProductEntry(
        id = "nutella", barcode = "3017620422003",
        name = "Nutella Hazelnut & Cocoa Spread", brand = "Ferrero",
        category = "Sweet Spreads", domain = "Food", emoji = "🍫",
        score = 18, nutritionScore = 0, additiveScore = 18, organicScore = 0,
        isOrganic = false, containsPalmOil = true, containsGluten = false, containsLactose = true,
        ingredients = "Sugar (56.3%), Palm Oil, Hazelnuts (13%), Skimmed Milk Powder (8.7%), Cocoa (7.4%), Lecithin (E322), Vanillin.",
        additives = listOf(SeedAdditives[5]),
        healthierAltIds = listOf("nocciolata", "almond-butter")
    ),
    ProductEntry(
        id = "nocciolata", barcode = "8009004800151",
        name = "Organic 45% Hazelnut & Cacao Spread", brand = "Rigoni di Asiago Bio",
        category = "Sweet Spreads", domain = "Food", emoji = "🌰",
        score = 86, nutritionScore = 46, additiveScore = 30, organicScore = 10,
        isOrganic = true, containsPalmOil = false, containsGluten = false, containsLactose = false,
        ingredients = "Organic Roasted Hazelnuts (45%), Organic Cane Sugar, Cold-Pressed Sunflower Oil, Organic Cocoa (11%), Cocoa Butter.",
        additives = emptyList(),
        healthierAltIds = listOf("almond-butter")
    ),
    ProductEntry(
        id = "almond-butter", barcode = "5060132281001",
        name = "100% Stone-Ground Organic Almond Butter", brand = "Meridian Organic",
        category = "Sweet Spreads", domain = "Food", emoji = "🥜",
        score = 96, nutritionScore = 56, additiveScore = 30, organicScore = 10,
        isOrganic = true, containsPalmOil = false, containsGluten = false, containsLactose = false,
        ingredients = "100% Organic Roasted Whole Skin-On Almonds. Zero palm oil, zero added sugar.",
        additives = emptyList()
    ),
    ProductEntry(
        id = "cola", barcode = "5449000000996",
        name = "Coca-Cola Original Taste", brand = "Coca-Cola",
        category = "Beverages", domain = "Food", emoji = "🥤",
        score = 6, nutritionScore = 0, additiveScore = 6, organicScore = 0,
        isOrganic = false, containsPalmOil = false, containsGluten = false, containsLactose = false,
        ingredients = "Carbonated Water, Sugar (10.6g/100ml), Caramel E150d, Phosphoric Acid E338, Caffeine.",
        additives = listOf(SeedAdditives[3]),
        healthierAltIds = listOf("avocado")
    ),
    ProductEntry(
        id = "lotion-bad", barcode = "4005808132645",
        name = "Perfumed Body Milk Lotion", brand = "Conventional Beauty",
        category = "Skincare", domain = "Cosmetic", emoji = "🧴",
        score = 19, nutritionScore = 0, additiveScore = 19, organicScore = 0,
        isOrganic = false, containsPalmOil = false, containsGluten = false, containsLactose = false,
        ingredients = "Aqua, Paraffinum Liquidum, Glycerin, Dimethicone, Phenoxyethanol, Propylparaben, BHT, Parfum, Linalool.",
        additives = listOf(SeedAdditives[4]),
        healthierAltIds = listOf("ceramide-cream")
    ),
    ProductEntry(
        id = "ceramide-cream", barcode = "3337875597388",
        name = "Ceramide + Niacinamide Barrier Cream", brand = "La Roche-Posay / CeraVe",
        category = "Skincare", domain = "Cosmetic", emoji = "✨",
        score = 93, nutritionScore = 0, additiveScore = 93, organicScore = 0,
        isOrganic = false, containsPalmOil = false, containsGluten = false, containsLactose = false,
        ingredients = "Aqua, Glycerin, Shea Butter, Niacinamide, Panthenol, Ceramide NP, Sodium Hyaluronate.",
        additives = listOf(SeedAdditives[6])
    ),
    ProductEntry(
        id = "avocado", barcode = "PLU-4225",
        name = "Fresh Hass Avocado (Raw Whole Produce)", brand = "USDA Whole Foods · Clean Fifteen #1",
        category = "Whole Foods", domain = "WholeFood", emoji = "🥑",
        score = 98, nutritionScore = 58, additiveScore = 30, organicScore = 10,
        isOrganic = true, containsPalmOil = false, containsGluten = false, containsLactose = false,
        ingredients = "100% Whole Raw Hass Avocado. Rich in Potassium (485mg), Folate, Oleic Acid (Omega-9), Lutein.",
        additives = emptyList(),
        wholeFoodNote = "Glycemic Index: 15 · Clean Fifteen #1 (<1% detectable pesticide residue)"
    ),
    ProductEntry(
        id = "salmon", barcode = "PLU-SALMON",
        name = "Wild-Caught Salmon Fillet (Raw Protein)", brand = "USDA Whole Foods · Marine Omega-3",
        category = "Whole Foods", domain = "WholeFood", emoji = "🐟",
        score = 95, nutritionScore = 55, additiveScore = 30, organicScore = 10,
        isOrganic = true, containsPalmOil = false, containsGluten = false, containsLactose = false,
        ingredients = "100% Wild-Caught Salmon. Rich in EPA/DHA Omega-3 (2,200mg), Vitamin D3, B12, Selenium, Astaxanthin.",
        additives = emptyList(),
        wholeFoodNote = "Glycemic Index: 0 · Low Mercury Marine Tier · Bake gently at 175°C"
    )
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            YarzarMobileApp()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YarzarMobileApp() {
    val scope = rememberCoroutineScope()
    var isDark by remember { mutableStateOf(true) }
    var lang by remember { mutableStateOf("en") }
    var currentTab by remember { mutableIntStateOf(0) }
    var catalog by remember { mutableStateOf(SeedCatalog) }
    var selectedProduct by remember { mutableStateOf(SeedCatalog.first()) }
    var liveResults by remember { mutableStateOf<List<ProductEntry>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var barcodeInput by remember { mutableStateOf("") }
    var customIngredients by remember { mutableStateOf("") }

    val colorScheme = if (isDark) {
        darkColorScheme(primary = Emerald, secondary = MintTeal)
    } else {
        lightColorScheme(primary = Emerald, secondary = MintTeal)
    }

    MaterialTheme(colorScheme = colorScheme) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text("Yarzar", fontWeight = FontWeight.Bold, fontSize = 19.sp)
                            Text(
                                if (lang == "tr") "Gıda, Kozmetik ve Doğal Besin Tarayıcısı"
                                else "Food, Cosmetic & Whole-Food Scanner",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    actions = {
                        TextButton(onClick = { isDark = !isDark }) {
                            Text(if (isDark) (if (lang == "tr") "🌙 Koyu" else "🌙 Dark") else "☀️ Normal")
                        }
                        TextButton(onClick = { lang = if (lang == "en") "tr" else "en" }) {
                            Text("🌐 ${lang.uppercase()}", fontWeight = FontWeight.Bold)
                        }
                    }
                )
            },
            bottomBar = {
                NavigationBar {
                    NavigationBarItem(
                        selected = currentTab == 0,
                        onClick = { currentTab = 0 },
                        icon = { Icon(Icons.Default.QrCodeScanner, null) },
                        label = { Text(if (lang == "tr") "Tara" else "Scan") }
                    )
                    NavigationBarItem(
                        selected = currentTab == 1,
                        onClick = { currentTab = 1 },
                        icon = { Icon(Icons.Default.Inventory2, null) },
                        label = { Text(if (lang == "tr") "Katalog" else "Catalog") }
                    )
                    NavigationBarItem(
                        selected = currentTab == 2,
                        onClick = { currentTab = 2 },
                        icon = { Icon(Icons.Default.Eco, null) },
                        label = { Text(if (lang == "tr") "Doğal" else "Whole") }
                    )
                    NavigationBarItem(
                        selected = currentTab == 3,
                        onClick = { currentTab = 3 },
                        icon = { Icon(Icons.Default.Science, null) },
                        label = { Text(if (lang == "tr") "Katkılar" else "Additives") }
                    )
                    NavigationBarItem(
                        selected = currentTab == 4,
                        onClick = { currentTab = 4 },
                        icon = { Icon(Icons.Default.Info, null) },
                        label = { Text(if (lang == "tr") "Hakkında" else "About") }
                    )
                }
            }
        ) { padding ->
            Box(modifier = Modifier.padding(padding).fillMaxSize()) {
                when (currentTab) {
                    0 -> ScannerScreen(
                        lang = lang,
                        selected = selectedProduct,
                        barcodeInput = barcodeInput,
                        onBarcodeChange = { barcodeInput = it },
                        isSearching = isSearching,
                        statusMessage = statusMessage,
                        liveResults = liveResults,
                        onSelectProduct = { selectedProduct = it },
                        onUpdateProduct = { updated ->
                            selectedProduct = updated
                            catalog = catalog.map { if (it.id == updated.id) updated else it }
                        },
                        onLiveLookup = { query ->
                            scope.launch {
                                isSearching = true
                                statusMessage = null
                                val found = OpenFactsAndroidClient.lookupOrSearch(query)
                                isSearching = false
                                if (found.isNotEmpty()) {
                                    liveResults = found
                                    selectedProduct = found.first()
                                    statusMessage = if (lang == "tr") "OpenFacts üzerinden ${found.size} gerçek ürün bulundu."
                                    else "Fetched ${found.size} real product(s) live from OpenFacts."
                                } else {
                                    val local = catalog.firstOrNull {
                                        it.barcode.contains(query, true) || it.name.contains(query, true)
                                    }
                                    if (local != null) {
                                        selectedProduct = local
                                    } else {
                                        statusMessage = if (lang == "tr") "Eşleşme bulunamadı, aşağıdan etiket analizi yapabilirsiniz."
                                        else "No match found; try custom ingredient label analysis below."
                                    }
                                }
                            }
                        },
                        customIngredients = customIngredients,
                        onCustomIngredientsChange = { customIngredients = it }
                    )
                    1 -> CatalogScreen(
                        lang = lang,
                        items = catalog,
                        onSelect = {
                            selectedProduct = it
                            currentTab = 0
                        }
                    )
                    2 -> CatalogScreen(
                        lang = lang,
                        items = catalog.filter { it.domain == "WholeFood" },
                        onSelect = {
                            selectedProduct = it
                            currentTab = 0
                        }
                    )
                    3 -> AdditivesScreen(lang = lang)
                    else -> AboutScreen(lang = lang)
                }
            }
        }
    }
}

@Composable
fun ScoreRing(score: Int, size: Dp = 74.dp) {
    val col = scoreColor(score)
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(size)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawArc(
                color = col.copy(alpha = 0.16f),
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = 18f)
            )
            drawArc(
                color = col,
                startAngle = -90f,
                sweepAngle = (score / 100f) * 360f,
                useCenter = false,
                style = Stroke(width = 18f, cap = StrokeCap.Round)
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$score", fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = col)
            Text("/ 100", fontSize = 9.sp)
        }
    }
}

@Composable
fun ScannerScreen(
    lang: String,
    selected: ProductEntry,
    barcodeInput: String,
    onBarcodeChange: (String) -> Unit,
    isSearching: Boolean,
    statusMessage: String?,
    liveResults: List<ProductEntry>,
    onSelectProduct: (ProductEntry) -> Unit,
    onUpdateProduct: (ProductEntry) -> Unit,
    onLiveLookup: (String) -> Unit,
    customIngredients: String,
    onCustomIngredientsChange: (String) -> Unit
) {
    val tiers = listOf("Trace (<0.5%)", "Minor (0.5–2%)", "High (>2%)")
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Card(shape = RoundedCornerShape(16.dp)) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = barcodeInput,
                            onValueChange = onBarcodeChange,
                            placeholder = {
                                Text(
                                    if (lang == "tr") "Barkod veya ürün/marka ara (ör. Nutella, Ülker, CeraVe)..."
                                    else "Enter barcode OR search 3M+ products (e.g. Nutella, Ülker, CeraVe)..."
                                )
                            },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        Button(
                            onClick = { onLiveLookup(barcodeInput) },
                            enabled = !isSearching && barcodeInput.isNotBlank()
                        ) {
                            Text(if (isSearching) "..." else if (lang == "tr") "Ara" else "Lookup")
                        }
                    }

                    if (statusMessage != null) {
                        Text(statusMessage, fontSize = 12.sp, color = Emerald, fontWeight = FontWeight.SemiBold)
                    }

                    if (liveResults.isNotEmpty()) {
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            liveResults.forEach { item ->
                                AssistChip(
                                    onClick = { onSelectProduct(item) },
                                    label = { Text("${item.emoji} ${item.name} (${item.score})") }
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            Card(shape = RoundedCornerShape(16.dp)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(62.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(scoreColor(selected.score).copy(alpha = 0.14f))
                        ) {
                            Text(selected.emoji, fontSize = 32.sp)
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(selected.name, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                            Text("${selected.brand} · ${selected.barcode}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    ratingLabel(selected.score, lang),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = scoreColor(selected.score)
                                )
                                OutlinedButton(
                                    onClick = {
                                        val toggled = selected.copy(isOrganic = !selected.isOrganic)
                                        onUpdateProduct(recalculateProductEntry(toggled))
                                    },
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        if (selected.isOrganic) (if (lang == "tr") "Organik (+10)" else "Organic (+10)")
                                        else (if (lang == "tr") "Organik Yap" else "Set Organic"),
                                        fontSize = 10.sp
                                    )
                                }
                            }
                        }
                        ScoreRing(score = selected.score)
                    }

                    if (selected.domain != "Cosmetic") {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            PillarChip(
                                title = if (lang == "tr") "Besin (60%)" else "Nutrition (60%)",
                                value = "${selected.nutritionScore}/60",
                                color = Emerald,
                                modifier = Modifier.weight(1f)
                            )
                            PillarChip(
                                title = if (lang == "tr") "Katkı (30%)" else "Additives (30%)",
                                value = "${selected.additiveScore}/30",
                                color = MintTeal,
                                modifier = Modifier.weight(1f)
                            )
                            PillarChip(
                                title = if (lang == "tr") "Organik (10%)" else "Organic (10%)",
                                value = "${selected.organicScore}/10",
                                color = LimeGood,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    selected.wholeFoodNote?.let { note ->
                        Surface(
                            color = Emerald.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("🥑 $note", modifier = Modifier.padding(10.dp), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    if (selected.additives.isNotEmpty()) {
                        Text(
                            if (lang == "tr") "Doz Duyarlı Katkı Maddesi Analizi (Oranı Değiştirin)"
                            else "Dose-Aware Additive Analysis (Tap Tier to Recalculate)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        selected.additives.forEach { add ->
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                                        Text("${add.code} · ${add.name}", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        Text(add.risk, fontSize = 11.sp, color = AmberMediocre, fontWeight = FontWeight.Bold)
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        tiers.forEach { tier ->
                                            val isSel = add.concentrationTier == tier
                                            FilterChip(
                                                selected = isSel,
                                                onClick = {
                                                    val updatedAdditives = selected.additives.map {
                                                        if (it.code == add.code) it.copy(concentrationTier = tier) else it
                                                    }
                                                    onUpdateProduct(recalculateProductEntry(selected.copy(additives = updatedAdditives)))
                                                },
                                                label = { Text(tier, fontSize = 10.sp) }
                                            )
                                        }
                                    }
                                    Text(add.summary, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }

                    Text(
                        if (lang == "tr") "İçindekiler: ${selected.ingredients}"
                        else "Ingredients: ${selected.ingredients}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item {
            Card(shape = RoundedCornerShape(16.dp)) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        if (lang == "tr") "Özel İçindekiler / Etiket Analizörü"
                        else "Custom Ingredient Label Analyzer",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    OutlinedTextField(
                        value = customIngredients,
                        onValueChange = onCustomIngredientsChange,
                        placeholder = {
                            Text(
                                if (lang == "tr") "İçindekiler listesini buraya yapıştırın (ör. E250, E322, Niacinamide)..."
                                else "Paste ingredient or INCI list here (e.g. E250, E322, Niacinamide)..."
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = {
                            val lower = customIngredients.lowercase()
                            val matched = SeedAdditives.filter {
                                lower.contains(it.code.lowercase()) || lower.contains(it.name.split(" ").first().lowercase())
                            }
                            val base = ProductEntry(
                                id = "custom-${System.currentTimeMillis()}",
                                barcode = "OCR-LABEL",
                                name = if (lang == "tr") "Analiz Edilen Etiket" else "Analyzed Label Product",
                                brand = "Yarzar Live Analyzer",
                                category = "Custom Analysis",
                                domain = "Food",
                                emoji = "🔬",
                                score = 70,
                                nutritionScore = if (lower.contains("sugar") || lower.contains("şeker")) 22 else 48,
                                additiveScore = 30,
                                organicScore = 0,
                                isOrganic = false,
                                containsPalmOil = lower.contains("palm"),
                                containsGluten = lower.contains("wheat") || lower.contains("gluten"),
                                containsLactose = lower.contains("milk") || lower.contains("süt"),
                                ingredients = customIngredients,
                                additives = matched
                            )
                            onSelectProduct(recalculateProductEntry(base))
                        },
                        enabled = customIngredients.isNotBlank(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (lang == "tr") "İçindekileri Analiz Et" else "Analyze Ingredients")
                    }
                }
            }
        }
    }
}

@Composable
fun PillarChip(title: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Surface(
        color = color.copy(alpha = 0.12f),
        shape = RoundedCornerShape(10.dp),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(title, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, color = color)
        }
    }
}

@Composable
fun CatalogScreen(lang: String, items: List<ProductEntry>, onSelect: (ProductEntry) -> Unit) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(items) { item ->
            Card(
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().clickable { onSelect(item) }
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(item.emoji, fontSize = 28.sp)
                    Column(modifier = Modifier.weight(1f)) {
                        Text(item.name, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text("${item.brand} · ${item.barcode}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    ScoreRing(score = item.score, size = 52.dp)
                }
            }
        }
    }
}

@Composable
fun AdditivesScreen(lang: String) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(SeedAdditives) { add ->
            Card(shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text("${add.code} — ${add.name}", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text(add.concentrationTier, fontSize = 11.sp, color = MintTeal, fontWeight = FontWeight.SemiBold)
                    }
                    Text("${add.category} · ${add.risk}", fontSize = 12.sp, color = CrimsonPoor, fontWeight = FontWeight.SemiBold)
                    Text(add.summary, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
fun AboutScreen(lang: String) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        if (lang == "tr") "YARZAR HAKKINDA" else "ABOUT YARZAR",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = CrimsonPoor
                    )
                    Text("Yarzar (Yarar & Zarar) v1.6.3", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    Text(
                        if (lang == "tr") "100% Bağımsız Gıda, Kozmetik ve Doğal Besin Sağlık Tarayıcısı"
                        else "100% Independent Food, Cosmetic & Whole-Food Health Scanner",
                        fontSize = 13.sp,
                        color = Emerald
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                    Text(
                        if (lang == "tr") "Geliştirici: Doç. Dr. Mehmet Akif Çifçi (@themanoftalent)"
                        else "Developer: Assoc. Prof. Dr. Mehmet Akif Cifci (@themanoftalent)",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp
                    )
                    Text(
                        "GitHub: https://github.com/themanoftalent/Yarzar",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}
