package com.producthunter.ai

import android.os.Bundle
import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val colors = darkColorScheme(
                primary = Color(0xFF34D399),
                secondary = Color(0xFF60A5FA),
                background = Color(0xFF0B0E13),
                surface = Color(0xFF121722),
                surfaceVariant = Color(0xFF1B2230)
            )
            MaterialTheme(colorScheme = colors) {
                ProductHunterApp()
            }
        }
    }
}

private enum class Screen(val label: String, val glyph: String) {
    HOME("Inicio", "⌂"), DISCOVER("Descubrir", "⌕"), ANALYZE("Analizar", "◎"), LIBRARY("Biblioteca", "▣"), TOOLS("Herramientas", "⋯"),
    CALCULATOR("Calculadora", "$"), REVIEWS("Reseñas", "★"), COMPARE("Comparar", "⇄"), SOURCES("Fuentes", "↗")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProductHunterApp() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val repository = remember { ProductRepository(context) }
    val connectorStore = remember { ConnectorConfigStore(context) }
    val liveClient = remember { LiveResearchClient() }
    var products by remember { mutableStateOf(repository.getAll()) }
    var screen by remember { mutableStateOf(Screen.HOME) }

    fun refresh() { products = repository.getAll() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Product Hunter AI", fontWeight = FontWeight.Bold)
                        Text(screen.label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        bottomBar = {
            if (screen in listOf(Screen.HOME, Screen.DISCOVER, Screen.ANALYZE, Screen.LIBRARY, Screen.TOOLS)) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    listOf(Screen.HOME, Screen.DISCOVER, Screen.ANALYZE, Screen.LIBRARY, Screen.TOOLS).forEach { item ->
                        NavigationBarItem(
                            selected = screen == item,
                            onClick = { screen = item },
                            icon = { Text(item.glyph, fontSize = 20.sp) },
                            label = { Text(item.label, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (screen) {
                Screen.HOME -> DashboardScreen(products, onAnalyze = { screen = Screen.ANALYZE }, onCalculator = { screen = Screen.CALCULATOR })
                Screen.DISCOVER -> LiveResearchScreen(connectorStore, liveClient)
                Screen.ANALYZE -> AnalyzeScreen(onSave = { repository.save(it); refresh(); screen = Screen.LIBRARY })
                Screen.LIBRARY -> LibraryScreen(products, onDelete = { repository.delete(it); refresh() })
                Screen.TOOLS -> ToolsScreen { screen = it }
                Screen.CALCULATOR -> CalculatorScreen(onBack = { screen = Screen.TOOLS })
                Screen.REVIEWS -> ReviewScreen(onBack = { screen = Screen.TOOLS })
                Screen.COMPARE -> CompareScreen(products, onBack = { screen = Screen.TOOLS })
                Screen.SOURCES -> ConnectorSettingsScreen(connectorStore, onBack = { screen = Screen.TOOLS })
            }
        }
    }
}

@Composable
private fun DashboardScreen(products: List<ProductCandidate>, onAnalyze: () -> Unit, onCalculator: () -> Unit) {
    val sorted = products.sortedByDescending { ProductScorer.score(it).total }
    val avg = if (products.isEmpty()) 0 else products.map { ProductScorer.score(it).total }.average().toInt()
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            HeroCard(
                title = "Encuentra productos con datos, no intuición",
                subtitle = "Evalúa demanda, margen, competencia, contenido, logística y capacidad de crear una marca.",
                action = "Analizar producto",
                onAction = onAnalyze
            )
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricCard("Guardados", products.size.toString(), Modifier.weight(1f))
                MetricCard("Score medio", "$avg/100", Modifier.weight(1f))
                val best = sorted.firstOrNull()?.let { ProductScorer.score(it).total.toInt() } ?: 0
                MetricCard("Mejor", "$best", Modifier.weight(1f))
            }
        }
        item { SectionTitle("Oportunidades") }
        if (sorted.isEmpty()) {
            item { EmptyCard("Todavía no tienes productos analizados.") }
        } else {
            items(sorted.take(4)) { product -> ProductSummaryCard(product) }
        }
        item {
            OutlinedButton(onClick = onCalculator, modifier = Modifier.fillMaxWidth()) {
                Text("Abrir calculadora de rentabilidad")
            }
        }
        item { Spacer(Modifier.height(12.dp)) }
    }
}

@Composable
private fun LiveResearchScreen(store: ConnectorConfigStore, client: LiveResearchClient) {
    var query by remember { mutableStateOf("") }
    var config by remember { mutableStateOf(store.load()) }
    var loading by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<LiveResearchResult?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            HeroCard(
                title = "Investigación real, en vivo",
                subtitle = "Cruza Google Trends, Google Shopping, Amazon, TikTok, Reddit y tu Shopify. Cada dato conserva su fuente y la app distingue evidencia real de estimaciones.",
                action = null,
                onAction = {}
            )
        }
        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Producto — ej. portable car vacuum") },
                singleLine = true
            )
        }
        item {
            Button(
                onClick = {
                    loading = true
                    error = null
                    config = store.load()
                    client.research(query.trim(), config) { outcome ->
                        loading = false
                        outcome.onSuccess { result = it }.onFailure { error = it.message ?: "Error de red" }
                    }
                },
                enabled = query.isNotBlank() && !loading,
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (loading) "Investigando…" else "Investigar ahora") }
        }
        error?.let { message -> item { EmptyCard("Error: $message") } }
        result?.let { r ->
            val derived = MarketSignalEngine.derive(r)
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), shape = RoundedCornerShape(24.dp)) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Market Signal Score", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                            Text("${r.marketSignalScore}", fontSize = 42.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                            Text("/100", Modifier.padding(bottom = 7.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.weight(1f))
                            Text("${r.evidenceCount} evidencias", fontWeight = FontWeight.Bold)
                        }
                        Text("Este score mide evidencia disponible y fuerza de señales; no es una predicción de ventas.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MetricCard("Demanda", "${derived.demand}/10", Modifier.weight(1f))
                    MetricCard("Saturación", "${derived.saturation}/10", Modifier.weight(1f))
                    MetricCard("Problema", "${derived.problemEvidence}/10", Modifier.weight(1f))
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MetricCard("Video/social", "${derived.socialVideo}/10", Modifier.weight(1f))
                    MetricCard("Confianza", "${derived.confidence}%", Modifier.weight(1f))
                }
            }
            if (r.marketPriceMedian != null) {
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        MetricCard("Precio mediano", money(r.marketPriceMedian), Modifier.weight(1f))
                        MetricCard("Mínimo", money(r.marketPriceMin ?: 0.0), Modifier.weight(1f))
                        MetricCard("Máximo", money(r.marketPriceMax ?: 0.0), Modifier.weight(1f))
                    }
                }
            }
            item { Text("Las señales automáticas son estimaciones derivadas de la evidencia mostrada; puedes usarlas como punto de partida, no como hechos absolutos.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            item { SectionTitle("Fuentes") }
            r.reports.forEach { report ->
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text(report.source, Modifier.weight(1f), fontWeight = FontWeight.Bold)
                                Text(sourceStateLabel(report.state), color = if (report.state == SourceState.LIVE) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                            }
                            Text(report.summary, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                            report.error?.let { Text(it, fontSize = 11.sp, color = MaterialTheme.colorScheme.error) }
                            report.evidence.take(5).forEach { e ->
                                Divider()
                                Text(e.title, fontWeight = FontWeight.Medium)
                                if (e.detail.isNotBlank()) Text(e.detail, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (e.url.isNotBlank()) {
                                    TextButton(onClick = {
                                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(e.url))) }
                                    }) { Text("Abrir fuente") }
                                }
                            }
                            if (report.evidence.size > 5) Text("+ ${report.evidence.size - 5} evidencias adicionales", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            item { SectionTitle("Limitaciones") }
            items(r.caveats) { caveat -> Text("• $caveat", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp) }
        }
        item {
            Text("Configura las fuentes en Herramientas → Fuentes y conectores. Google Trends funciona sin API key; otras fuentes pueden requerir autorización.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item { Spacer(Modifier.height(20.dp)) }
    }
}

private fun sourceStateLabel(state: SourceState): String = when (state) {
    SourceState.LIVE -> "EN VIVO"
    SourceState.NEEDS_AUTH -> "CONECTAR"
    SourceState.LIMITED -> "LIMITADO"
    SourceState.ERROR -> "ERROR"
    SourceState.DISABLED -> "DESACTIVADO"
}

@Composable
private fun AnalyzeScreen(onSave: (ProductCandidate) -> Unit) {
    var name by remember { mutableStateOf("") }
    var niche by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("49.99") }
    var cost by remember { mutableStateOf("12") }
    var shipping by remember { mutableStateOf("3") }
    var packaging by remember { mutableStateOf("1.5") }
    var ads by remember { mutableStateOf("14") }
    var problem by remember { mutableStateOf(7f) }
    var demand by remember { mutableStateOf(7f) }
    var video by remember { mutableStateOf(7f) }
    var differentiation by remember { mutableStateOf(6f) }
    var saturation by remember { mutableStateOf(5f) }
    var logistics by remember { mutableStateOf(7f) }
    var bundles by remember { mutableStateOf(6f) }
    var repeat by remember { mutableStateOf(4f) }

    val candidate = ProductCandidate(
        name = name.ifBlank { "Producto sin nombre" }, niche = niche,
        sellingPrice = price.toDoubleOrNull() ?: 0.0, productCost = cost.toDoubleOrNull() ?: 0.0,
        shippingCost = shipping.toDoubleOrNull() ?: 0.0, packagingCost = packaging.toDoubleOrNull() ?: 0.0,
        adCost = ads.toDoubleOrNull() ?: 0.0, problemImportance = problem.toInt(), demand = demand.toInt(),
        videoPotential = video.toInt(), differentiation = differentiation.toInt(), saturation = saturation.toInt(),
        logistics = logistics.toInt(), bundles = bundles.toInt(), repeatPurchase = repeat.toInt()
    )
    val score = ProductScorer.score(candidate)
    val finance = FinancialEngine.analyze(candidate.sellingPrice, candidate.productCost, candidate.shippingCost, candidate.packagingCost, candidate.adCost)

    Column(
        Modifier.fillMaxSize().padding(horizontal = 16.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ScoreHero(score.total, ProductScorer.verdict(score.total))
        OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text("Nombre del producto") })
        OutlinedTextField(niche, { niche = it }, Modifier.fillMaxWidth(), label = { Text("Nicho") })
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MoneyField("Precio", price, { price = it }, Modifier.weight(1f))
            MoneyField("Costo", cost, { cost = it }, Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MoneyField("Envío", shipping, { shipping = it }, Modifier.weight(1f))
            MoneyField("Empaque", packaging, { packaging = it }, Modifier.weight(1f))
        }
        MoneyField("CPA / publicidad estimada", ads, { ads = it }, Modifier.fillMaxWidth())
        SectionTitle("Señales del producto")
        ScoreSlider("Problema real", problem) { problem = it }
        ScoreSlider("Demanda", demand) { demand = it }
        ScoreSlider("Potencial de video / UGC", video) { video = it }
        ScoreSlider("Diferenciación", differentiation) { differentiation = it }
        ScoreSlider("Saturación (menos es mejor)", saturation) { saturation = it }
        ScoreSlider("Logística", logistics) { logistics = it }
        ScoreSlider("Bundles / upsells", bundles) { bundles = it }
        ScoreSlider("Recompra", repeat) { repeat = it }
        SectionTitle("Resultado financiero")
        FinanceCard(finance)
        SectionTitle("Desglose del score")
        ScoreBreakdownCard(score)
        Button(onClick = { onSave(candidate) }, enabled = name.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
            Text("Guardar análisis")
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun LibraryScreen(products: List<ProductCandidate>, onDelete: (Long) -> Unit) {
    if (products.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) { EmptyCard("No hay productos guardados todavía.") }
        return
    }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items(products.sortedByDescending { ProductScorer.score(it).total }, key = { it.id }) { product ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(product.name, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                            if (product.niche.isNotBlank()) Text(product.niche, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                        }
                        ScorePill(ProductScorer.score(product).total.toInt())
                    }
                    val f = FinancialEngine.analyze(product.sellingPrice, product.productCost, product.shippingCost, product.packagingCost, product.adCost)
                    Text("Precio ${money(product.sellingPrice)}  •  Ganancia estimada ${money(f.netProfit)}  •  Margen ${f.netMarginPercent.toInt()}%")
                    TextButton(onClick = { onDelete(product.id) }, modifier = Modifier.align(Alignment.End)) { Text("Eliminar") }
                }
            }
        }
        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun ToolsScreen(onOpen: (Screen) -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ToolCard("Calculadora", "Ganancia, margen, CPA máximo y ROAS de equilibrio", "$", { onOpen(Screen.CALCULATOR) })
        ToolCard("Analizador de reseñas", "Convierte quejas en mejoras concretas del producto", "★", { onOpen(Screen.REVIEWS) })
        ToolCard("Comparador", "Ordena tus productos por score, margen y beneficio", "⇄", { onOpen(Screen.COMPARE) })
        ToolCard("Fuentes y conectores", "Configura datos reales: Shopping, Amazon, Reddit, TikTok y Shopify", "↗", { onOpen(Screen.SOURCES) })
    }
}

@Composable
private fun CalculatorScreen(onBack: () -> Unit) {
    var price by remember { mutableStateOf("49.99") }
    var cost by remember { mutableStateOf("12") }
    var shipping by remember { mutableStateOf("3") }
    var packaging by remember { mutableStateOf("1.5") }
    var cpa by remember { mutableStateOf("14") }
    val r = FinancialEngine.analyze(price.toDoubleOrNull() ?: 0.0, cost.toDoubleOrNull() ?: 0.0, shipping.toDoubleOrNull() ?: 0.0, packaging.toDoubleOrNull() ?: 0.0, cpa.toDoubleOrNull() ?: 0.0)
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        BackButton(onBack)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MoneyField("Precio", price, { price = it }, Modifier.weight(1f)); MoneyField("Costo", cost, { cost = it }, Modifier.weight(1f))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MoneyField("Envío", shipping, { shipping = it }, Modifier.weight(1f)); MoneyField("Empaque", packaging, { packaging = it }, Modifier.weight(1f))
        }
        MoneyField("CPA publicidad", cpa, { cpa = it }, Modifier.fillMaxWidth())
        FinanceCard(r)
        SectionTitle("Escenarios de publicidad")
        listOf(10.0, 15.0, 20.0, 30.0).forEach { scenario ->
            val s = FinancialEngine.analyze(r.revenue, cost.toDoubleOrNull() ?: 0.0, shipping.toDoubleOrNull() ?: 0.0, packaging.toDoubleOrNull() ?: 0.0, scenario)
            KeyValueRow("CPA ${money(scenario)}", "Ganancia ${money(s.netProfit)}")
        }
    }
}

@Composable
private fun ReviewScreen(onBack: () -> Unit) {
    var text by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<ReviewAnalysis?>(null) }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        BackButton(onBack)
        Text("Pega reseñas", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text("Una reseña por línea. Funciona en español e inglés con un analizador local de temas.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(text, { text = it }, Modifier.fillMaxWidth().height(220.dp), label = { Text("Reseñas / comentarios") })
        Button(onClick = { result = ReviewAnalyzer.analyze(text) }, enabled = text.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Analizar reseñas") }
        result?.let { r ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MetricCard("Reseñas", r.totalLines.toString(), Modifier.weight(1f)); MetricCard("Señales negativas", r.negativeSignals.toString(), Modifier.weight(1f))
            }
            SectionTitle("Quejas detectadas")
            if (r.themes.isEmpty()) EmptyCard("No detecté temas conocidos. Puedes añadirlos manualmente al análisis del producto.")
            r.themes.forEach { theme ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.padding(14.dp)) {
                        Row(Modifier.fillMaxWidth()) { Text(theme.label, Modifier.weight(1f), fontWeight = FontWeight.Bold); Text("${theme.sharePercent}%", color = MaterialTheme.colorScheme.primary) }
                        Text("${theme.count} menciones", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(6.dp)); Text(theme.improvement)
                    }
                }
            }
            SectionTitle("Product V2 — mejoras sugeridas")
            r.suggestedImprovements.forEach { Text("• $it") }
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun CompareScreen(products: List<ProductCandidate>, onBack: () -> Unit) {
    val sorted = products.sortedByDescending { ProductScorer.score(it).total }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        BackButton(onBack)
        Spacer(Modifier.height(8.dp))
        Text("Comparador", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        if (sorted.isEmpty()) EmptyCard("Guarda productos primero para compararlos.")
        else LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(sorted) { p ->
                val s = ProductScorer.score(p)
                val f = FinancialEngine.analyze(p.sellingPrice, p.productCost, p.shippingCost, p.packagingCost, p.adCost)
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text(p.name, Modifier.weight(1f), fontWeight = FontWeight.Bold); ScorePill(s.total.toInt()) }
                        KeyValueRow("Ganancia estimada", money(f.netProfit)); KeyValueRow("Margen", "${f.netMarginPercent.toInt()}%")
                        KeyValueRow("CPA máximo aprox.", money(f.breakEvenCpa)); KeyValueRow("Saturación", "${p.saturation}/10")
                    }
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun ConnectorSettingsScreen(store: ConnectorConfigStore, onBack: () -> Unit) {
    var config by remember { mutableStateOf(store.load()) }
    var saved by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        BackButton(onBack)
        Text("Fuentes y conectores", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text("No se incluyen claves privadas dentro del APK. Puedes usar las fuentes públicas directamente o añadir tus propias credenciales. Para una versión pública, usa el backend incluido en el proyecto.", color = MaterialTheme.colorScheme.onSurfaceVariant)

        OutlinedTextField(config.country, { config = config.copy(country = it.take(2).uppercase()) }, Modifier.fillMaxWidth(), label = { Text("País (US, ES, MX…)") }, singleLine = true)

        SectionTitle("Google Shopping + Amazon")
        Text("SerpAPI es opcional. Cuando se configura, la app obtiene resultados estructurados con precios, ratings, reseñas y vendedores.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(config.serpApiKey, { config = config.copy(serpApiKey = it) }, Modifier.fillMaxWidth(), label = { Text("SerpAPI key") }, singleLine = true)

        SectionTitle("Reddit")
        Text("Reddit requiere OAuth. Usa un bearer token autorizado; no se hace scraping anónimo.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(config.redditBearerToken, { config = config.copy(redditBearerToken = it) }, Modifier.fillMaxWidth(), label = { Text("OAuth bearer token") }, singleLine = true)
        OutlinedTextField(config.redditUsername, { config = config.copy(redditUsername = it) }, Modifier.fillMaxWidth(), label = { Text("Usuario Reddit para User-Agent") }, singleLine = true)

        SectionTitle("Tu Shopify")
        Text("Consulta tu propio catálogo mediante GraphQL Admin API. El token necesita read_products.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(config.shopifyStoreDomain, { config = config.copy(shopifyStoreDomain = it) }, Modifier.fillMaxWidth(), label = { Text("tienda.myshopify.com") }, singleLine = true)
        OutlinedTextField(config.shopifyAdminToken, { config = config.copy(shopifyAdminToken = it) }, Modifier.fillMaxWidth(), label = { Text("Admin API access token") }, singleLine = true)

        SectionTitle("Backend seguro (opcional)")
        Text("Si rellenas este campo, la app usará /research en tu servidor y las claves anteriores pueden quedarse únicamente en variables de entorno del backend.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(config.backendUrl, { config = config.copy(backendUrl = it) }, Modifier.fillMaxWidth(), label = { Text("https://tu-backend.example.com") }, singleLine = true)

        Button(onClick = { store.save(config); saved = true }, modifier = Modifier.fillMaxWidth()) { Text(if (saved) "Guardado ✓" else "Guardar conectores") }

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Qué funciona sin credenciales", fontWeight = FontWeight.Bold)
                Text("""• Google Trends: feed público Trending Now
• TikTok Creative Center: enlace oficial y acceso limitado

Con credenciales:
• Google Shopping / Amazon: SerpAPI
• Reddit: OAuth
• Shopify: Admin API""", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text("Seguridad: esta versión de desarrollo guarda configuración localmente. Para distribuir la app, configura el backend y no guardes secretos administrativos dentro de clientes móviles.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun HeroCard(title: String, subtitle: String, action: String?, onAction: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, fontSize = 24.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (action != null) Button(onClick = onAction) { Text(action) }
        }
    }
}

@Composable
private fun ScoreHero(score: Double, verdict: String) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), shape = RoundedCornerShape(24.dp)) {
        Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text("Product Score", color = MaterialTheme.colorScheme.onSurfaceVariant); Text(verdict, fontSize = 18.sp, fontWeight = FontWeight.Bold) }
            Text(score.toInt().toString(), fontSize = 42.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
            Text("/100", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ProductSummaryCard(product: ProductCandidate) {
    val score = ProductScorer.score(product)
    val f = FinancialEngine.analyze(product.sellingPrice, product.productCost, product.shippingCost, product.packagingCost, product.adCost)
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) { Text(product.name, Modifier.weight(1f), fontWeight = FontWeight.Bold); ScorePill(score.total.toInt()) }
            Text(ProductScorer.verdict(score.total), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            Text("${money(product.sellingPrice)} venta • ${money(f.netProfit)} ganancia est. • ${f.netMarginPercent.toInt()}% margen")
        }
    }
}

@Composable
private fun FinanceCard(r: FinancialResult) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            KeyValueRow("Ingresos", money(r.revenue)); KeyValueRow("Costo puesto + procesamiento", money(r.landedCost))
            KeyValueRow("Antes de publicidad", money(r.contributionBeforeAds)); Divider(); KeyValueRow("Ganancia estimada", money(r.netProfit), true)
            KeyValueRow("Margen neto", "${"%.1f".format(Locale.US, r.netMarginPercent)}%")
            KeyValueRow("CPA máximo aprox.", money(r.breakEvenCpa)); KeyValueRow("ROAS de equilibrio", "${"%.2f".format(Locale.US, r.breakEvenRoas)}x")
        }
    }
}

@Composable
private fun ScoreBreakdownCard(s: ScoreBreakdown) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            KeyValueRow("Problema", "${s.problem.toInt()}/20"); KeyValueRow("Demanda", "${s.demand.toInt()}/15"); KeyValueRow("Margen", "${s.margin.toInt()}/15")
            KeyValueRow("Video/UGC", "${s.video.toInt()}/15"); KeyValueRow("Diferenciación", "${s.differentiation.toInt()}/10"); KeyValueRow("Competencia", "${s.competition.toInt()}/10")
            KeyValueRow("Logística", "${s.logistics.toInt()}/5"); KeyValueRow("Bundles", "${s.bundles.toInt()}/5"); KeyValueRow("Recompra", "${s.repeatPurchase.toInt()}/5")
        }
    }
}

@Composable
private fun ScoreSlider(label: String, value: Float, onChange: (Float) -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth()) { Text(label, Modifier.weight(1f)); Text(value.toInt().toString(), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold) }
        Slider(value = value, onValueChange = onChange, valueRange = 0f..10f, steps = 9)
    }
}

@Composable
private fun MoneyField(label: String, value: String, onValueChange: (String) -> Unit, modifier: Modifier) {
    OutlinedTextField(value = value, onValueChange = { if (it.isEmpty() || it.matches(Regex("\\d*(\\.\\d*)?"))) onValueChange(it) }, modifier = modifier, label = { Text(label) }, prefix = { Text("$") }, singleLine = true)
}

@Composable
private fun MetricCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(13.dp)) { Text(value, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary); Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
private fun ToolCard(title: String, description: String, glyph: String, onClick: () -> Unit) {
    Card(onClick = onClick, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant) { Text(glyph, Modifier.padding(12.dp), fontSize = 22.sp, color = MaterialTheme.colorScheme.primary) }
            Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.Bold); Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp) }
            Text("›", fontSize = 24.sp)
        }
    }
}

@Composable
private fun ScorePill(score: Int) {
    Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)) { Text("$score/100", Modifier.padding(horizontal = 10.dp, vertical = 6.dp), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold) }
}

@Composable
private fun KeyValueRow(key: String, value: String, strong: Boolean = false) {
    Row(Modifier.fillMaxWidth()) { Text(key, Modifier.weight(1f), color = if (strong) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = if (strong) FontWeight.Bold else FontWeight.Normal); Text(value, fontWeight = if (strong) FontWeight.Bold else FontWeight.Medium, color = if (strong) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface) }
}

@Composable
private fun SectionTitle(text: String) { Text(text, fontSize = 18.sp, fontWeight = FontWeight.Bold) }

@Composable
private fun EmptyCard(text: String) { Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) { Text(text, Modifier.padding(18.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) } }

@Composable
private fun BackButton(onBack: () -> Unit) { TextButton(onClick = onBack) { Text("‹ Volver") } }

private fun money(value: Double): String = "$" + String.format(Locale.US, "%,.2f", value)
