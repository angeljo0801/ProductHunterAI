package com.producthunter.ai

import org.junit.Assert.assertTrue
import org.junit.Test

class EnginesTest {
    @Test
    fun scoreStaysWithinOneHundred() {
        val p = ProductCandidate(
            name = "Test", sellingPrice = 60.0, productCost = 10.0,
            problemImportance = 10, demand = 10, videoPotential = 10,
            differentiation = 10, saturation = 0, logistics = 10,
            bundles = 10, repeatPurchase = 10
        )
        val score = ProductScorer.score(p).total
        assertTrue(score in 0.0..100.0)
    }

    @Test
    fun financeCalculatesPositiveProfit() {
        val f = FinancialEngine.analyze(50.0, 10.0, 3.0, 1.0, 12.0)
        assertTrue(f.netProfit > 0)
        assertTrue(f.breakEvenCpa > 0)
    }
}
