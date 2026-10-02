package com.producthunter.ai

import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors
import kotlin.math.ln

class LiveResearchClient {
    private val executor = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    fun research(query: String, config: ConnectorConfig, callback: (Result<LiveResearchResult>) -> Unit) {
        executor.execute {
            try {
                val result = if (config.backendUrl.isNotBlank()) {
                    backendResearch(query, config)
                } else {
                    directResearch(query, config)
                }
                main.post { callback(Result.success(result)) }
            } catch (e: Exception) {
                main.post { callback(Result.failure(e)) }
            }
        }
    }

    private fun directResearch(query: String, config: ConnectorConfig): LiveResearchResult {
        val reports = mutableListOf<SourceReport>()
        reports += googleTrendsReport(query, config.country)
        reports += if (config.serpApiKey.isNotBlank()) googleShoppingReport(query, config) else SourceReport(
            "Google Shopping", SourceState.NEEDS_AUTH,
            "Añade una API key de SerpAPI para precios, vendedores, ratings y reseñas de Google Shopping."
        )
        reports += if (config.serpApiKey.isNotBlank()) amazonReport(query, config) else SourceReport(
            "Amazon", SourceState.NEEDS_AUTH,
            "Añade una API key de SerpAPI para consultar resultados reales de Amazon sin hacer scraping directo desde el teléfono."
        )
        reports += tiktokReport(query, config)
        reports += if (config.redditBearerToken.isNotBlank()) redditReport(query, config) else SourceReport(
            "Reddit", SourceState.NEEDS_AUTH,
            "Reddit exige OAuth. Pega un bearer token autorizado para analizar discusiones públicas relacionadas con el producto."
        )
        reports += if (config.shopifyStoreDomain.isNotBlank() && config.shopifyAdminToken.isNotBlank()) {
            shopifyReport(query, config)
        } else SourceReport(
            "Tu Shopify", SourceState.NEEDS_AUTH,
            "Conecta el dominio .myshopify.com y un Admin API access token para comparar la oportunidad con tu propio catálogo."
        )
        return aggregate(query, reports)
    }

    private fun backendResearch(query: String, config: ConnectorConfig): LiveResearchResult {
        val url = "${config.backendUrl}/research?q=${enc(query)}&country=${enc(config.country)}"
        val json = JSONObject(httpGet(url, mapOf("Accept" to "application/json")))
        val reports = json.optJSONArray("reports")?.let(::parseReports) ?: emptyList()
        return LiveResearchResult(
            query = query,
            reports = reports,
            marketPriceMedian = json.optNullableDouble("market_price_median"),
            marketPriceMin = json.optNullableDouble("market_price_min"),
            marketPriceMax = json.optNullableDouble("market_price_max"),
            reviewCountSignal = json.optInt("review_count_signal", 0),
            evidenceCount = json.optInt("evidence_count", reports.sumOf { it.evidence.size }),
            marketSignalScore = json.optInt("market_signal_score", 0),
            caveats = json.optJSONArray("caveats")?.strings() ?: emptyList()
        )
    }

    private fun googleTrendsReport(query: String, country: String): SourceReport = try {
        val xml = httpGet("https://trends.google.com/trending/rss?geo=${enc(country)}", mapOf("Accept" to "application/rss+xml, application/xml"))
        val itemRegex = Regex("<item>(.*?)</item>", RegexOption.DOT_MATCHES_ALL)
        val titleRegex = Regex("<title>(.*?)</title>", RegexOption.DOT_MATCHES_ALL)
        val trafficRegex = Regex("<(?:ht:)?approx_traffic>(.*?)</(?:ht:)?approx_traffic>", RegexOption.DOT_MATCHES_ALL)
        val items = itemRegex.findAll(xml).mapNotNull { m ->
            val block = m.groupValues[1]
            val title = titleRegex.find(block)?.groupValues?.getOrNull(1)?.decodeXml()?.trim() ?: return@mapNotNull null
            val traffic = trafficRegex.find(block)?.groupValues?.getOrNull(1)?.decodeXml()?.trim().orEmpty()
            EvidenceItem("Google Trends", title, if (traffic.isBlank()) "Trending Now" else "Volumen aprox.: $traffic", "https://trends.google.com/trending?geo=${enc(country)}")
        }.take(25).toList()
        val tokens = query.lowercase().split(Regex("\\s+")).filter { it.length >= 3 }
        val matching = items.filter { item -> tokens.any { token -> item.title.lowercase().contains(token) } }
        SourceReport(
            "Google Trends", SourceState.LIVE,
            if (matching.isNotEmpty()) "Encontré ${matching.size} coincidencia(s) en Trending Now para $country." else "Fuente en vivo. El término no aparece entre las tendencias recientes de $country; esto no significa que no tenga demanda estable.",
            if (matching.isNotEmpty()) matching else items.take(5)
        )
    } catch (e: Exception) {
        SourceReport("Google Trends", SourceState.ERROR, "No se pudo leer el feed de tendencias en este intento.", error = e.safeMessage())
    }

    private fun googleShoppingReport(query: String, config: ConnectorConfig): SourceReport = try {
        val url = "https://serpapi.com/search.json?engine=google_shopping&q=${enc(query)}&gl=${enc(config.country.lowercase())}&hl=en&api_key=${enc(config.serpApiKey)}"
        val json = JSONObject(httpGet(url))
        val arr = json.optJSONArray("shopping_results") ?: json.optJSONArray("inline_shopping_results") ?: JSONArray()
        val evidence = (0 until minOf(arr.length(), 20)).map { i ->
            val o = arr.optJSONObject(i) ?: JSONObject()
            EvidenceItem(
                source = "Google Shopping",
                title = o.optString("title", "Producto"),
                detail = listOf(o.optString("source"), o.optString("price"), if (o.has("reviews")) "${o.optInt("reviews")} reseñas" else "").filter { it.isNotBlank() }.joinToString(" • "),
                url = o.optString("product_link", o.optString("link", "")),
                price = o.optNullableDouble("extracted_price"),
                rating = o.optNullableDouble("rating"),
                reviews = o.optNullableInt("reviews")
            )
        }
        SourceReport("Google Shopping", SourceState.LIVE, "${evidence.size} resultados comerciales reales encontrados.", evidence)
    } catch (e: Exception) {
        SourceReport("Google Shopping", SourceState.ERROR, "No se pudieron cargar resultados de Shopping.", error = e.safeMessage())
    }

    private fun amazonReport(query: String, config: ConnectorConfig): SourceReport = try {
        val url = "https://serpapi.com/search.json?engine=amazon&k=${enc(query)}&amazon_domain=amazon.com&language=en_US&api_key=${enc(config.serpApiKey)}"
        val json = JSONObject(httpGet(url))
        val arr = json.optJSONArray("organic_results") ?: JSONArray()
        val productEvidence = (0 until minOf(arr.length(), 20)).map { i ->
            val o = arr.optJSONObject(i) ?: JSONObject()
            val priceObj = o.optJSONObject("price")
            val price = priceObj?.optNullableDouble("extracted_price") ?: o.optNullableDouble("extracted_price")
            val reviews = o.optNullableInt("reviews") ?: o.optNullableInt("reviews_count")
            EvidenceItem(
                source = "Amazon",
                title = o.optString("title", "Producto Amazon"),
                detail = listOfNotNull(
                    price?.let { "$${"%.2f".format(it)}" },
                    o.optNullableDouble("rating")?.let { "$it★" },
                    reviews?.let { "$it reseñas" }
                ).joinToString(" • "),
                url = o.optString("link", ""),
                price = price,
                rating = o.optNullableDouble("rating"),
                reviews = reviews
            )
        }
        val asins = (0 until minOf(arr.length(), 2)).mapNotNull { i -> arr.optJSONObject(i)?.optString("asin")?.takeIf { it.isNotBlank() } }
        val reviewEvidence = mutableListOf<EvidenceItem>()
        asins.forEach { asin ->
            runCatching {
                val productUrl = "https://serpapi.com/search.json?engine=amazon_product&asin=${enc(asin)}&amazon_domain=amazon.com&api_key=${enc(config.serpApiKey)}"
                val productJson = JSONObject(httpGet(productUrl))
                val reviewsInfo = productJson.optJSONObject("reviews_information")
                val summary = reviewsInfo?.optJSONObject("summary")
                val insights = summary?.optJSONArray("insights") ?: JSONArray()
                for (j in 0 until minOf(insights.length(), 6)) {
                    val insight = insights.optJSONObject(j) ?: continue
                    val sentiment = insight.optString("sentiment", "unknown")
                    val mentions = insight.optJSONObject("mentions")
                    val negative = mentions?.optInt("negative", 0) ?: 0
                    val total = mentions?.optInt("total", 0) ?: 0
                    if (sentiment.equals("positive", true) && negative == 0) continue
                    val examples = insight.optJSONArray("examples")
                    val firstExample = examples?.optJSONObject(0)
                    reviewEvidence += EvidenceItem(
                        source = "Amazon Review Insight",
                        title = insight.optString("title", "Tema de reseñas"),
                        detail = listOf(
                            "Sentimiento: $sentiment",
                            if (total > 0) "$negative negativas de $total menciones" else "",
                            insight.optString("summary", "")
                        ).filter { it.isNotBlank() }.joinToString(" • "),
                        url = firstExample?.optString("link", "").orEmpty(),
                        reviews = if (negative > 0) negative else total.takeIf { it > 0 }
                    )
                }
            }
        }
        val evidence = productEvidence + reviewEvidence
        SourceReport("Amazon", SourceState.LIVE, "${productEvidence.size} competidores y ${reviewEvidence.size} temas de reseñas encontrados.", evidence)
    } catch (e: Exception) {
        SourceReport("Amazon", SourceState.ERROR, "No se pudieron cargar resultados de Amazon.", error = e.safeMessage())
    }

    private fun tiktokReport(query: String, config: ConnectorConfig): SourceReport {
        if (config.serpApiKey.isBlank()) {
            return SourceReport(
                "TikTok Creative Center", SourceState.LIMITED,
                "Creative Center es público, pero TikTok no ofrece una API pública estable para automatizar todo Top Products/Top Ads. La app conserva el enlace oficial; añade SerpAPI para buscar evidencia pública indexada.",
                evidence = listOf(EvidenceItem("TikTok Creative Center", "Abrir Creative Center", "Top Ads y tendencias públicas", "https://ads.tiktok.com/business/creativecenter/inspiration/topads/pc/en?region=${enc(config.country)}"))
            )
        }
        return try {
            val q = "site:ads.tiktok.com/business/creativecenter ${query}"
            val url = "https://serpapi.com/search.json?engine=google&q=${enc(q)}&gl=${enc(config.country.lowercase())}&hl=en&api_key=${enc(config.serpApiKey)}"
            val json = JSONObject(httpGet(url))
            val arr = json.optJSONArray("organic_results") ?: JSONArray()
            val evidence = (0 until minOf(arr.length(), 10)).map { i ->
                val o = arr.optJSONObject(i) ?: JSONObject()
                EvidenceItem("TikTok Creative Center", o.optString("title", "TikTok"), o.optString("snippet", ""), o.optString("link", ""))
            }
            SourceReport("TikTok Creative Center", if (evidence.isEmpty()) SourceState.LIMITED else SourceState.LIVE, if (evidence.isEmpty()) "No encontré evidencia indexada para este término; abre Creative Center para revisar manualmente." else "${evidence.size} referencias públicas indexadas relacionadas con el término.", evidence)
        } catch (e: Exception) {
            SourceReport("TikTok Creative Center", SourceState.ERROR, "No se pudo consultar evidencia pública de TikTok.", error = e.safeMessage())
        }
    }

    private fun redditReport(query: String, config: ConnectorConfig): SourceReport = try {
        val userAgent = "android:com.producthunter.ai:v0.2.0 (by /u/${config.redditUsername.ifBlank { "producthunter_user" }})"
        val url = "https://oauth.reddit.com/search?q=${enc(query)}&sort=relevance&t=year&limit=25&type=link"
        val json = JSONObject(httpGet(url, mapOf("Authorization" to "Bearer ${config.redditBearerToken}", "User-Agent" to userAgent)))
        val children = json.optJSONObject("data")?.optJSONArray("children") ?: JSONArray()
        val evidence = (0 until minOf(children.length(), 20)).mapNotNull { i ->
            val d = children.optJSONObject(i)?.optJSONObject("data") ?: return@mapNotNull null
            EvidenceItem(
                source = "Reddit",
                title = d.optString("title", "Post"),
                detail = "r/${d.optString("subreddit")} • ${d.optInt("score")} pts • ${d.optInt("num_comments")} comentarios",
                url = "https://www.reddit.com${d.optString("permalink", "")}",
                reviews = d.optInt("num_comments")
            )
        }
        SourceReport("Reddit", SourceState.LIVE, "${evidence.size} discusiones públicas encontradas mediante OAuth.", evidence)
    } catch (e: Exception) {
        SourceReport("Reddit", SourceState.ERROR, "El token OAuth puede haber expirado o la consulta falló.", error = e.safeMessage())
    }

    private fun shopifyReport(query: String, config: ConnectorConfig): SourceReport = try {
        val domain = config.shopifyStoreDomain.removePrefix("https://").removePrefix("http://").trimEnd('/')
        val endpoint = "https://$domain/admin/api/2026-10/graphql.json"
        val safeQuery = query.replace("\\", "\\\\").replace("\"", "\\\"")
        val gql = """query { products(first: 10, query: "$safeQuery") { nodes { id title handle vendor productType variants(first: 3) { nodes { title price } } } } }"""
        val body = JSONObject().put("query", gql).toString()
        val json = JSONObject(httpPost(endpoint, body, mapOf("X-Shopify-Access-Token" to config.shopifyAdminToken, "Content-Type" to "application/json")))
        val nodes = json.optJSONObject("data")?.optJSONObject("products")?.optJSONArray("nodes") ?: JSONArray()
        val evidence = (0 until nodes.length()).mapNotNull { i ->
            val o = nodes.optJSONObject(i) ?: return@mapNotNull null
            val firstVariant = o.optJSONObject("variants")?.optJSONArray("nodes")?.optJSONObject(0)
            EvidenceItem("Tu Shopify", o.optString("title"), listOf(o.optString("vendor"), o.optString("productType"), firstVariant?.optString("price").orEmpty()).filter { it.isNotBlank() }.joinToString(" • "), "https://$domain/products/${o.optString("handle")}", firstVariant?.optString("price")?.toDoubleOrNull())
        }
        SourceReport("Tu Shopify", SourceState.LIVE, "${evidence.size} producto(s) relacionados encontrados en tu catálogo.", evidence)
    } catch (e: Exception) {
        SourceReport("Tu Shopify", SourceState.ERROR, "No se pudo consultar el catálogo. Revisa dominio, token y permiso read_products.", error = e.safeMessage())
    }

    private fun aggregate(query: String, reports: List<SourceReport>): LiveResearchResult {
        val evidence = reports.flatMap { it.evidence }
        val prices = evidence.mapNotNull { it.price }.filter { it > 0 }.sorted()
        val median = when {
            prices.isEmpty() -> null
            prices.size % 2 == 1 -> prices[prices.size / 2]
            else -> (prices[prices.size / 2 - 1] + prices[prices.size / 2]) / 2.0
        }
        val reviews = evidence.sumOf { it.reviews ?: 0 }
        val liveSources = reports.count { it.state == SourceState.LIVE }
        val evidenceScore = (evidence.size.coerceAtMost(40) / 40.0 * 35).toInt()
        val sourceScore = (liveSources.coerceAtMost(5) / 5.0 * 25).toInt()
        val reviewScore = ((ln((reviews + 1).toDouble()) / ln(100_001.0)) * 25).toInt().coerceIn(0, 25)
        val priceScore = if (prices.size >= 5) 15 else prices.size * 3
        val signal = (evidenceScore + sourceScore + reviewScore + priceScore).coerceIn(0, 100)
        val caveats = buildList {
            add("Market Signal Score mide cantidad/calidad de evidencia disponible; no predice ventas ni sustituye una prueba real de mercado.")
            if (reports.any { it.source == "Google Trends" && it.state == SourceState.LIVE }) add("Google Trends Trending Now detecta picos recientes; una ausencia no implica baja demanda estable.")
            if (reports.any { it.source.startsWith("TikTok") && it.state != SourceState.LIVE }) add("TikTok tiene acceso automatizado limitado sin una API/servicio autorizado adicional.")
        }
        return LiveResearchResult(query, reports = reports, marketPriceMedian = median, marketPriceMin = prices.firstOrNull(), marketPriceMax = prices.lastOrNull(), reviewCountSignal = reviews, evidenceCount = evidence.size, marketSignalScore = signal, caveats = caveats)
    }

    private fun parseReports(arr: JSONArray): List<SourceReport> = (0 until arr.length()).mapNotNull { i ->
        val o = arr.optJSONObject(i) ?: return@mapNotNull null
        val evArr = o.optJSONArray("evidence") ?: JSONArray()
        val ev = (0 until evArr.length()).mapNotNull { j ->
            val e = evArr.optJSONObject(j) ?: return@mapNotNull null
            EvidenceItem(e.optString("source", o.optString("source")), e.optString("title"), e.optString("detail"), e.optString("url"), e.optNullableDouble("price"), e.optNullableDouble("rating"), e.optNullableInt("reviews"))
        }
        SourceReport(o.optString("source"), runCatching { SourceState.valueOf(o.optString("state", "LIMITED")) }.getOrDefault(SourceState.LIMITED), o.optString("summary"), ev, o.optString("error").ifBlank { null })
    }

    private fun httpGet(url: String, headers: Map<String, String> = emptyMap()): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 12_000
        connection.readTimeout = 18_000
        connection.setRequestProperty("User-Agent", "ProductHunterAI/0.2 Android")
        headers.forEach { (k, v) -> connection.setRequestProperty(k, v) }
        return readResponse(connection)
    }

    private fun httpPost(url: String, body: String, headers: Map<String, String> = emptyMap()): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.connectTimeout = 12_000
        connection.readTimeout = 18_000
        connection.doOutput = true
        connection.setRequestProperty("User-Agent", "ProductHunterAI/0.2 Android")
        headers.forEach { (k, v) -> connection.setRequestProperty(k, v) }
        connection.outputStream.use { it.write(body.toByteArray(StandardCharsets.UTF_8)) }
        return readResponse(connection)
    }

    private fun readResponse(connection: HttpURLConnection): String {
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val body = BufferedReader(InputStreamReader(stream ?: throw IllegalStateException("HTTP $code"))).use { it.readText() }
        if (code !in 200..299) throw IllegalStateException("HTTP $code: ${body.take(300)}")
        return body
    }

    private fun enc(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8.toString())
    private fun Exception.safeMessage(): String = message?.take(220) ?: this::class.java.simpleName
}

private fun JSONObject.optNullableDouble(key: String): Double? = if (!has(key) || isNull(key)) null else optDouble(key).takeIf { !it.isNaN() }
private fun JSONObject.optNullableInt(key: String): Int? = if (!has(key) || isNull(key)) null else optInt(key)
private fun JSONArray.strings(): List<String> = (0 until length()).mapNotNull { optString(it).takeIf(String::isNotBlank) }
private fun String.decodeXml(): String = replace(Regex("^<!\\[CDATA\\[(.*)]]>$", RegexOption.DOT_MATCHES_ALL), "$1").replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'").replace("&lt;", "<").replace("&gt;", ">")
