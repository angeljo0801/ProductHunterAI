package com.producthunter.ai

import kotlin.math.ln

data class DerivedMarketSignals(
    val demand: Int,
    val saturation: Int,
    val problemEvidence: Int,
    val socialVideo: Int,
    val confidence: Int,
    val explanation: List<String>
)

object MarketSignalEngine {
    fun derive(result: LiveResearchResult): DerivedMarketSignals {
        val evidence = result.reports.flatMap { it.evidence }
        val amazon = evidence.count { it.source.contains("Amazon", ignoreCase = true) }
        val shopping = evidence.count { it.source.contains("Shopping", ignoreCase = true) }
        val reddit = evidence.count { it.source.contains("Reddit", ignoreCase = true) }
        val tiktok = evidence.count { it.source.contains("TikTok", ignoreCase = true) }
        val trends = result.reports.firstOrNull { it.source == "Google Trends" }
        val reviewVolume = evidence.sumOf { it.reviews ?: 0 }
        val liveSources = result.reports.count { it.state == SourceState.LIVE }

        val reviewDemand = if (reviewVolume <= 0) 0 else ((ln(reviewVolume + 1.0) / ln(100_001.0)) * 5.0).toInt().coerceIn(0, 5)
        val marketBreadth = ((amazon + shopping).coerceAtMost(20) / 4).coerceIn(0, 4)
        val trendBoost = if (trends?.summary?.contains("coincidencia", ignoreCase = true) == true) 1 else 0
        val demand = (reviewDemand + marketBreadth + trendBoost).coerceIn(0, 10)

        val competitionItems = amazon + shopping
        val saturation = when {
            competitionItems >= 30 -> 10
            competitionItems >= 22 -> 9
            competitionItems >= 16 -> 8
            competitionItems >= 12 -> 7
            competitionItems >= 8 -> 6
            competitionItems >= 5 -> 5
            competitionItems >= 3 -> 4
            competitionItems >= 1 -> 3
            else -> 1
        }

        val problemEvidence = when {
            reddit >= 10 -> 8
            reddit >= 5 -> 7
            reddit >= 2 -> 6
            evidence.any { it.source.contains("Review", ignoreCase = true) } -> 6
            reviewVolume > 1000 -> 5
            reviewVolume > 0 -> 4
            else -> 2
        }

        val socialVideo = when {
            tiktok >= 5 -> 9
            tiktok >= 2 -> 8
            tiktok == 1 -> 7
            result.reports.any { it.source.contains("TikTok") && it.state == SourceState.LIMITED } -> 4
            else -> 3
        }

        val confidence = ((liveSources * 15) + (result.evidenceCount.coerceAtMost(40) * 1.5).toInt()).coerceIn(0, 100)
        val explanation = buildList {
            add("Demanda: señal combinada de volumen de reseñas, amplitud de resultados y tendencias recientes.")
            add("Saturación: aproximación basada en competidores observados, no en el número total de vendedores del mercado.")
            add("Problema: usa discusiones/reseñas disponibles; no supone que cada comentario sea una queja.")
            add("Video/social: usa evidencia indexada de TikTok cuando está disponible.")
        }
        return DerivedMarketSignals(demand, saturation, problemEvidence, socialVideo, confidence, explanation)
    }
}
