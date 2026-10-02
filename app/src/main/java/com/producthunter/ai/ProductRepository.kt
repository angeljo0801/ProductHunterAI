package com.producthunter.ai

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class ProductRepository(context: Context) {
    private val prefs = context.getSharedPreferences("product_hunter", Context.MODE_PRIVATE)

    fun getAll(): List<ProductCandidate> {
        val raw = prefs.getString("products", null) ?: return seedProducts()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { index -> fromJson(arr.getJSONObject(index)) }
        } catch (_: Exception) {
            seedProducts()
        }
    }

    fun save(product: ProductCandidate) {
        val items = getAll().toMutableList()
        val index = items.indexOfFirst { it.id == product.id }
        if (index >= 0) items[index] = product else items.add(0, product)
        persist(items)
    }

    fun delete(id: Long) {
        persist(getAll().filterNot { it.id == id })
    }

    private fun persist(items: List<ProductCandidate>) {
        val arr = JSONArray()
        items.forEach { arr.put(toJson(it)) }
        prefs.edit().putString("products", arr.toString()).apply()
    }

    private fun toJson(p: ProductCandidate) = JSONObject().apply {
        put("id", p.id); put("name", p.name); put("niche", p.niche)
        put("sellingPrice", p.sellingPrice); put("productCost", p.productCost)
        put("shippingCost", p.shippingCost); put("packagingCost", p.packagingCost); put("adCost", p.adCost)
        put("problemImportance", p.problemImportance); put("demand", p.demand); put("videoPotential", p.videoPotential)
        put("differentiation", p.differentiation); put("saturation", p.saturation); put("logistics", p.logistics)
        put("bundles", p.bundles); put("repeatPurchase", p.repeatPurchase); put("notes", p.notes)
    }

    private fun fromJson(o: JSONObject) = ProductCandidate(
        id = o.optLong("id", System.currentTimeMillis()),
        name = o.optString("name", "Producto"),
        niche = o.optString("niche"),
        sellingPrice = o.optDouble("sellingPrice"),
        productCost = o.optDouble("productCost"),
        shippingCost = o.optDouble("shippingCost"),
        packagingCost = o.optDouble("packagingCost"),
        adCost = o.optDouble("adCost"),
        problemImportance = o.optInt("problemImportance", 5),
        demand = o.optInt("demand", 5),
        videoPotential = o.optInt("videoPotential", 5),
        differentiation = o.optInt("differentiation", 5),
        saturation = o.optInt("saturation", 5),
        logistics = o.optInt("logistics", 5),
        bundles = o.optInt("bundles", 5),
        repeatPurchase = o.optInt("repeatPurchase", 5),
        notes = o.optString("notes")
    )

    private fun seedProducts() = listOf(
        ProductCandidate(
            id = 1,
            name = "Aspiradora compacta para auto",
            niche = "Automóvil",
            sellingPrice = 49.99,
            productCost = 11.0,
            shippingCost = 3.0,
            packagingCost = 1.5,
            adCost = 14.0,
            problemImportance = 8,
            demand = 8,
            videoPotential = 9,
            differentiation = 6,
            saturation = 7,
            logistics = 8,
            bundles = 8,
            repeatPurchase = 3,
            notes = "Ejemplo precargado. Edítalo o crea tus propios productos."
        )
    )
}
