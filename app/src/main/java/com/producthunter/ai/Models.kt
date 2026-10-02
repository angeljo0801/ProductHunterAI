package com.producthunter.ai

data class ProductCandidate(
    val id: Long = System.currentTimeMillis(),
    val name: String,
    val niche: String = "",
    val sellingPrice: Double = 0.0,
    val productCost: Double = 0.0,
    val shippingCost: Double = 0.0,
    val packagingCost: Double = 0.0,
    val adCost: Double = 0.0,
    val problemImportance: Int = 5,
    val demand: Int = 5,
    val videoPotential: Int = 5,
    val differentiation: Int = 5,
    val saturation: Int = 5,
    val logistics: Int = 5,
    val bundles: Int = 5,
    val repeatPurchase: Int = 5,
    val notes: String = ""
)

data class ScoreBreakdown(
    val problem: Double,
    val demand: Double,
    val margin: Double,
    val video: Double,
    val differentiation: Double,
    val competition: Double,
    val logistics: Double,
    val bundles: Double,
    val repeatPurchase: Double
) {
    val total: Double
        get() = listOf(problem, demand, margin, video, differentiation, competition, logistics, bundles, repeatPurchase).sum()
}

data class FinancialResult(
    val revenue: Double,
    val landedCost: Double,
    val contributionBeforeAds: Double,
    val netProfit: Double,
    val netMarginPercent: Double,
    val breakEvenCpa: Double,
    val breakEvenRoas: Double
)

data class ReviewTheme(
    val key: String,
    val label: String,
    val count: Int,
    val sharePercent: Int,
    val improvement: String
)

data class ReviewAnalysis(
    val totalLines: Int,
    val negativeSignals: Int,
    val themes: List<ReviewTheme>,
    val suggestedImprovements: List<String>
)
