package com.producthunter.ai

import android.content.Context

class ConnectorConfigStore(context: Context) {
    private val prefs = context.getSharedPreferences("product_hunter_connectors", Context.MODE_PRIVATE)

    fun load(): ConnectorConfig = ConnectorConfig(
        country = prefs.getString("country", "US") ?: "US",
        serpApiKey = prefs.getString("serp_api_key", "") ?: "",
        redditBearerToken = prefs.getString("reddit_bearer", "") ?: "",
        redditUsername = prefs.getString("reddit_username", "") ?: "",
        shopifyStoreDomain = prefs.getString("shopify_domain", "") ?: "",
        shopifyAdminToken = prefs.getString("shopify_token", "") ?: "",
        backendUrl = prefs.getString("backend_url", "") ?: ""
    )

    fun save(config: ConnectorConfig) {
        prefs.edit()
            .putString("country", config.country.trim().uppercase())
            .putString("serp_api_key", config.serpApiKey.trim())
            .putString("reddit_bearer", config.redditBearerToken.trim())
            .putString("reddit_username", config.redditUsername.trim())
            .putString("shopify_domain", config.shopifyStoreDomain.trim())
            .putString("shopify_token", config.shopifyAdminToken.trim())
            .putString("backend_url", config.backendUrl.trim().trimEnd('/'))
            .apply()
    }
}
