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

data class AdditiveEntry(
    val code: String,
    val name: String,
    val category: String,
    val risk: String,
    val concentrationTier: String,
    val summary: String,
    val isEndocrineDisruptor: Boolean = false
)

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

val SeedAdditives = listOf(
    AdditiveEntry("E250", "Sodium Nitrite", "Preservative", "High Risk", "Minor (0.5–2%)", "Forms nitrosamines; associated with elevated colorectal risk (WHO/IARC)."),
    AdditiveEntry("E320", "BHA", "Synthetic Antioxidant", "High Risk", "Trace (<0.5%)", "Suspected endocrine disruptor and potential carcinogen.", true),
    AdditiveEntry("E102", "Tartrazine", "Synthetic Dye", "High Risk", "Trace (<0.5%)", "Linked to hypersensitivity and hyperactivity in sensitive children."),
    AdditiveEntry("E150d", "Sulfite Ammonia Caramel", "Coloring", "Moderate Risk", "Minor (0.5–2%)", "Contains 4-MEI processing byproduct monitored by EFSA."),
    AdditiveEntry("INCI-PARABEN", "Propylparaben", "Cosmetic Preservative", "High Risk", "Minor (0.5–2%)", "Estrogenic endocrine-disrupting potential in leave-on cosmetics.", true),
    AdditiveEntry("E322", "Sunflower / Soy Lecithin", "Natural Emulsifier", "Risk-Free", "Trace (<0.5%)", "Safe phospholipid emulsifier with zero toxicity concern."),
    AdditiveEntry("INCI-NIACINAMIDE", "Niacinamide (Vitamin B3)", "Skin Barrier Active", "Risk-Free", "Significant (>2%)", "Strengthens ceramide lipid barrier and calms inflammation.")
)

val SeedCatalog = listOf(
    ProductEntry(
        id = "nutella", barcode = "3017620422003",
        name = "Hazelnut & Cocoa Sweet Spread", brand = "Ferrero Nutella",
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
        name = "Classic Carbonated Caramel Cola", brand = "Coca-Cola Original",
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
        name = "Fresh Hass Avocado (Raw Whole Produce)", brand = "Whole Foods · Clean Fifteen #1",
        category = "Whole Foods", domain = "WholeFood", emoji = "🥑",
        score = 98, nutritionScore = 58, additiveScore = 30, organicScore = 10,
        isOrganic = true, containsPalmOil = false, containsGluten = false, containsLactose = false,
        ingredients = "100% Whole Raw Hass Avocado. Rich in Potassium (485mg), Folate, Oleic Acid (Omega-9), Lutein.",
        additives = emptyList(),
        wholeFoodNote = "Glycemic Index: 15 · Clean Fifteen #1 (<1% detectable pesticide residue)"
    ),
    ProductEntry(
        id = "salmon", barcode = "PLU-SALMON",
        name = "Wild-Caught Salmon Fillet (Raw Protein)", brand = "Whole Foods · Marine Omega-3",
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
    var isDark by remember { mutableStateOf(true) }
    var lang by remember { mutableStateOf("en") }
    var currentTab by remember { mutableIntStateOf(0) }
    var selectedProduct by remember { mutableStateOf(SeedCatalog.first()) }
    var barcodeInput by remember { mutableStateOf("") }
    var customIngredients by remember { mutableStateOf("Sugar, Palm Oil, Sodium Nitrite (E250), Tartrazine (E102)") }

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
                        onSelectProduct = { selectedProduct = it },
                        customIngredients = customIngredients,
                        onCustomChange = { customIngredients = it }
                    )
                    1 -> CatalogScreen(lang = lang, items = SeedCatalog) {
                        selectedProduct = it
                        currentTab = 0
                    }
                    2 -> CatalogScreen(lang = lang, items = SeedCatalog.filter { it.domain == "WholeFood" }) {
                        selectedProduct = it
                        currentTab = 0
                    }
                    3 -> AdditivesScreen(lang = lang)
                    else -> AboutScreen(lang = lang)
                }
            }
        }
    }
}

@Composable
fun ScoreGauge(score: Int, size: Dp = 74.dp) {
    val col = scoreColor(score)
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(size)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawArc(
                color = col.copy(alpha = 0.18f),
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = 18f)
            )
            drawArc(
                color = col,
                startAngle = -90f,
                sweepAngle = (score.coerceIn(4, 100) / 100f) * 360f,
                useCenter = false,
                style = Stroke(width = 18f, cap = StrokeCap.Round)
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$score", fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, color = col)
            Text("/100", fontSize = 10.sp, fontFamily = FontFamily.Monospace)
        }
    }
}

@Composable
fun ScannerScreen(
    lang: String,
    selected: ProductEntry,
    barcodeInput: String,
    onBarcodeChange: (String) -> Unit,
    onSelectProduct: (ProductEntry) -> Unit,
    customIngredients: String,
    onCustomChange: (String) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Card(shape = RoundedCornerShape(14.dp)) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = barcodeInput,
                            onValueChange = onBarcodeChange,
                            placeholder = { Text("EAN-13 / UPC (3017620422003)") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        Button(onClick = {
                            SeedCatalog.firstOrNull { it.barcode.equals(barcodeInput.trim(), ignoreCase = true) }
                                ?.let(onSelectProduct)
                        }) {
                            Text(if (lang == "tr") "Sorgula" else "Lookup")
                        }
                    }

                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SeedCatalog.forEach { item ->
                            AssistChip(
                                onClick = {
                                    onBarcodeChange(item.barcode)
                                    onSelectProduct(item)
                                },
                                label = { Text("${item.emoji} ${item.name} (${item.score})") }
                            )
                        }
                    }
                }
            }
        }

        item {
            Card(shape = RoundedCornerShape(16.dp)) {
                Row(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("${selected.emoji} ${selected.name}", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Text("${selected.brand} · ${selected.barcode}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(6.dp))
                        Surface(
                            color = scoreColor(selected.score).copy(alpha = 0.16f),
                            shape = CircleShape
                        ) {
                            Text(
                                ratingLabel(selected.score, lang),
                                color = scoreColor(selected.score),
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                    ScoreGauge(score = selected.score)
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                PillarBox(Modifier.weight(1f), if (lang == "tr") "Besin (%60)" else "Nutrition (60%)", "${selected.nutritionScore}/60", Emerald)
                PillarBox(Modifier.weight(1f), if (lang == "tr") "Katkı (%30)" else "Additives (30%)", "${selected.additiveScore}", MintTeal)
                PillarBox(Modifier.weight(1f), if (lang == "tr") "Organik (%10)" else "Organic (10%)", "${selected.organicScore}/10", LimeGood)
            }
        }

        selected.wholeFoodNote?.let { wf ->
            item {
                Card(shape = RoundedCornerShape(14.dp)) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(if (lang == "tr") "🥑 Doğal Besin Avantajı" else "🥑 Whole Food Advantage", fontWeight = FontWeight.Bold, color = Emerald)
                        Text(wf, fontSize = 13.sp)
                    }
                }
            }
        }

        item {
            Card(shape = RoundedCornerShape(14.dp)) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(if (lang == "tr") "İçindekiler ve Doz Duyarlı Katkı Analizi" else "Ingredients & Dose-Aware Additive Analysis", fontWeight = FontWeight.Bold)
                    Text(selected.ingredients, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (selected.additives.isEmpty()) {
                        Text(if (lang == "tr") "✓ Zararlı Katkı Maddesi Yok" else "✓ Zero Harmful Additives Detected", color = Emerald, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    } else {
                        selected.additives.forEach { add ->
                            Text("• ${add.code} ${add.name} [${add.concentrationTier}] — ${add.risk}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = AmberMediocre)
                        }
                    }
                }
            }
        }

        val alts = selected.healthierAltIds.mapNotNull { id -> SeedCatalog.find { it.id == id } }
        if (alts.isNotEmpty()) {
            item {
                Card(shape = RoundedCornerShape(14.dp)) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(if (lang == "tr") "✨ Daha Sağlıklı Alternatifler" else "✨ Smart Healthier Alternatives", fontWeight = FontWeight.Bold, color = Emerald)
                        alts.forEach { alt ->
                            Row(
                                modifier = Modifier.fillMaxWidth().clickable { onSelectProduct(alt) }.padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("${alt.emoji} ${alt.name} (+${alt.score - selected.score} pts)", fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                ScoreGauge(score = alt.score, size = 44.dp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PillarBox(modifier: Modifier, title: String, value: String, color: Color) {
    Card(modifier = modifier, shape = RoundedCornerShape(12.dp)) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(title, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, color = color)
        }
    }
}

@Composable
fun CatalogScreen(lang: String, items: List<ProductEntry>, onSelect: (ProductEntry) -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(items) { item ->
            Card(
                modifier = Modifier.fillMaxWidth().clickable { onSelect(item) },
                shape = RoundedCornerShape(14.dp)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("${item.emoji} ${item.name}", fontWeight = FontWeight.Bold)
                        Text("${item.brand} · ${item.barcode}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    ScoreGauge(score = item.score, size = 50.dp)
                }
            }
        }
    }
}

@Composable
fun AdditivesScreen(lang: String) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(SeedAdditives) { add ->
            Card(shape = RoundedCornerShape(14.dp)) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${add.code} · ${add.name}", fontWeight = FontWeight.Bold)
                        Text("${add.concentrationTier} · ${add.risk}", fontSize = 11.sp, color = AmberMediocre, fontWeight = FontWeight.Bold)
                    }
                    Text(add.summary, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
fun AboutScreen(lang: String) {
    Column(
        modifier = Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(shape = RoundedCornerShape(14.dp)) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Yarzar v1.6.3", fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
                Text(if (lang == "tr") "Açık Kaynak | Özgürce Çatallayın ve Kullanın" else "Open Source | Free to Fork & Use", fontWeight = FontWeight.Bold, color = Emerald)
                Text("Developed by: SoftAI Yazılım")
                Text("Developer: Assoc. Prof. Dr. Mehmet Akif Cifci (@themanoftalent)")
                Text("GitHub: https://github.com/themanoftalent/yarzar-android")
            }
        }
    }
}
