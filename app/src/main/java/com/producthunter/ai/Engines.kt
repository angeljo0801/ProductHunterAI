package com.producthunter.ai

import kotlin.math.max
import kotlin.math.roundToInt

object FinancialEngine {
    fun analyze(
        price: Double,
        productCost: Double,
        shipping: Double,
        packaging: Double,
        adCost: Double,
        processingRate: Double = 0.03,
        processingFixed: Double = 0.30
    ): FinancialResult {
        val processing = if (price > 0) price * processingRate + processingFixed else 0.0
        val landed = productCost + shipping + packaging + processing
        val contribution = price - landed
        val net = contribution - adCost
        val margin = if (price > 0) (net / price) * 100.0 else 0.0
        val breakEvenCpa = max(0.0, contribution)
        val breakEvenRoas = if (breakEvenCpa > 0) price / breakEvenCpa else 0.0
        return FinancialResult(price, landed, contribution, net, margin, breakEvenCpa, breakEvenRoas)
    }
}

object ProductScorer {
    fun score(product: ProductCandidate): ScoreBreakdown {
        fun clamp(value: Int) = value.coerceIn(0, 10)
        val finance = FinancialEngine.analyze(
            price = product.sellingPrice,
            productCost = product.productCost,
            shipping = product.shippingCost,
            packaging = product.packagingCost,
            adCost = 0.0
        )
        val grossMarginPercent = if (product.sellingPrice > 0) {
            (finance.contributionBeforeAds / product.sellingPrice) * 100.0
        } else 0.0
        val marginScore = when {
            grossMarginPercent >= 70 -> 15.0
            grossMarginPercent >= 60 -> 13.5
            grossMarginPercent >= 50 -> 12.0
            grossMarginPercent >= 40 -> 9.5
            grossMarginPercent >= 30 -> 7.0
            grossMarginPercent >= 20 -> 4.0
            else -> 1.0
        }
        return ScoreBreakdown(
            problem = clamp(product.problemImportance) * 2.0,
            demand = clamp(product.demand) * 1.5,
            margin = marginScore,
            video = clamp(product.videoPotential) * 1.5,
            differentiation = clamp(product.differentiation) * 1.0,
            competition = (10 - clamp(product.saturation)) * 1.0,
            logistics = clamp(product.logistics) * 0.5,
            bundles = clamp(product.bundles) * 0.5,
            repeatPurchase = clamp(product.repeatPurchase) * 0.5
        )
    }

    fun verdict(score: Double): String = when {
        score >= 85 -> "Muy fuerte para validar"
        score >= 75 -> "Buena oportunidad"
        score >= 65 -> "Prometedor, necesita diferenciación"
        score >= 50 -> "Riesgo medio"
        else -> "Débil en su estado actual"
    }
}

object ReviewAnalyzer {
    private data class ThemeDef(val key: String, val label: String, val words: List<String>, val improvement: String)

    private val defs = listOf(
        ThemeDef("battery", "Batería", listOf("battery", "batería", "charge", "carga", "dies", "duración"), "Mejor batería, USB-C y autonomía claramente especificada"),
        ThemeDef("power", "Potencia / rendimiento", listOf("weak", "power", "potencia", "suction", "succión", "slow", "lento", "doesn't work", "no funciona"), "Aumentar rendimiento y demostrarlo con una prueba medible"),
        ThemeDef("quality", "Calidad / durabilidad", listOf("broke", "broken", "cheap", "plastic", "romp", "frágil", "calidad", "durability", "durabilidad"), "Mejorar materiales, garantía y control de calidad"),
        ThemeDef("fit", "Tamaño / ajuste", listOf("small", "large", "size", "fit", "tamaño", "cabe", "ajuste", "bulky", "grande"), "Añadir medidas claras, ajuste regulable o más variantes"),
        ThemeDef("cleaning", "Limpieza / mantenimiento", listOf("clean", "limpiar", "wash", "lavar", "filter", "filtro", "mess", "sucio"), "Hacer piezas lavables/desmontables y mantenimiento rápido"),
        ThemeDef("shipping", "Envío / empaque", listOf("shipping", "delivery", "arrived", "damaged", "envío", "llegó", "dañado", "caja"), "Mejor empaque y protección; aclarar tiempos de entrega"),
        ThemeDef("instructions", "Uso / instrucciones", listOf("instructions", "manual", "confusing", "how to", "instrucciones", "manual", "confuso", "usar"), "Simplificar onboarding, incluir guía visual y QR a video"),
        ThemeDef("price", "Precio / valor", listOf("expensive", "price", "worth", "precio", "caro", "vale"), "Aumentar valor percibido con bundle, garantía o accesorios")
    )

    fun analyze(text: String): ReviewAnalysis {
        val lines = text.lines().map { it.trim() }.filter { it.isNotBlank() }
        val normalized = lines.map { it.lowercase() }
        val counts = defs.associateWith { def ->
            normalized.count { line -> def.words.any { word -> line.contains(word) } }
        }.filterValues { it > 0 }
        val totalHits = counts.values.sum().coerceAtLeast(1)
        val themes = counts.entries
            .sortedByDescending { it.value }
            .map { (def, count) ->
                ReviewTheme(
                    key = def.key,
                    label = def.label,
                    count = count,
                    sharePercent = ((count.toDouble() / totalHits) * 100).roundToInt(),
                    improvement = def.improvement
                )
            }
        val negativeWords = listOf("bad", "terrible", "hate", "poor", "broke", "refund", "return", "malo", "horrible", "odio", "devol", "romp", "no funciona")
        val negativeSignals = normalized.count { line -> negativeWords.any { line.contains(it) } }
        return ReviewAnalysis(
            totalLines = lines.size,
            negativeSignals = negativeSignals,
            themes = themes,
            suggestedImprovements = themes.take(5).map { it.improvement }.distinct()
        )
    }
}

object OpportunityEngine {
    fun ideas(niche: String, painPoints: String): List<String> {
        val base = niche.trim().ifBlank { "tu nicho" }
        val pain = painPoints.lines().map { it.trim() }.filter { it.isNotBlank() }
        val generated = mutableListOf<String>()
        if (pain.isNotEmpty()) {
            pain.take(4).forEachIndexed { index, p ->
                generated += "${base.replaceFirstChar { it.uppercase() }} Pro ${index + 1}: producto diseñado para resolver ‘$p’"
            }
        }
        generated += "Kit completo de $base con accesorios complementarios y bundle premium"
        generated += "Versión compacta/portátil de un producto popular en $base"
        generated += "Versión premium de un producto existente en $base, enfocada en durabilidad y facilidad de uso"
        return generated.distinct().take(6)
    }
}
