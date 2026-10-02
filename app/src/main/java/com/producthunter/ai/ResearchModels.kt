package com.producthunter.ai

enum class SourceState { LIVE, NEEDS_AUTH, LIMITED, ERROR, DISABLED }

data class EvidenceItem(
    val source: String,
    val title: String,
    val detail: String = "",
    val url: String = "",
    val price: Double? = null,
    val rating: Double? = null,
    val reviews: Int? = null
)

data class SourceReport(
    val source: String,
    val state: SourceState,
    val summary: String,
    val evidence: List<EvidenceItem> = emptyList(),
    val error: String? = null
)

data class LiveResearchResult(
    val query: String,
    val generatedAt: Long = System.currentTimeMillis(),
    val reports: List<SourceReport>,
    val marketPriceMedian: Double? = null,
    val marketPriceMin: Double? = null,
    val marketPriceMax: Double? = null,
    val reviewCountSignal: Int = 0,
    val evidenceCount: Int = 0,
    val marketSignalScore: Int = 0,
    val caveats: List<String> = emptyList()
)

data class ConnectorConfig(
    val country: String = "US",
    val serpApiKey: String = "",
    val redditBearerToken: String = "",
    val redditUsername: String = "",
    val shopifyStoreDomain: String = "",
    val shopifyAdminToken: String = "",
    val backendUrl: String = ""
)
