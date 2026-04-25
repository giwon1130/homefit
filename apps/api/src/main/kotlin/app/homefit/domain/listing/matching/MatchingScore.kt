package app.homefit.domain.listing.matching

import app.homefit.domain.listing.Listing
import app.homefit.domain.listing.ListingUnit
import app.homefit.domain.listing.SupplyType
import app.homefit.domain.listing.eligibility.EligibilityEngine
import app.homefit.domain.profile.HouseholdMember
import app.homefit.domain.profile.HousingHistory
import app.homefit.domain.profile.Income
import app.homefit.domain.profile.Preferences
import app.homefit.domain.profile.ProfileCore

/**
 * Phase 2 v0 매칭 스코어. 통근 점수는 Phase 2 후속 PR에서 합산 (지오코딩+ODsay 키 받은 뒤).
 *
 * 0~100 점, 분배:
 *  - 자격(Eligibility) 30점 — bestSupplyType 따라 가중
 *  - 예산(Budget) 35점 — listing 최저가 vs preferences.maxPurchasePrice
 *  - 지역(Region) 35점 — listing.sido 가 사용자 선호 시도에 포함되는지
 */
data class MatchingScore(
    val eligibility: Int,
    val budget: Int,
    val region: Int,
    val total: Int,
    val bestSupplyType: SupplyType?,
    val notes: List<String>,
) {
    companion object {
        const val MAX_TOTAL = 100
        const val MAX_ELIG = 30
        const val MAX_BUDGET = 35
        const val MAX_REGION = 35
    }
}

class MatchingScoreCalculator(
    private val eligibilityEngine: EligibilityEngine,
) {
    fun calculate(
        listing: Listing,
        units: List<ListingUnit>,
        core: ProfileCore,
        members: List<HouseholdMember>,
        incomes: List<Income>,
        history: List<HousingHistory>,
        preferences: Preferences,
    ): MatchingScore {
        val notes = mutableListOf<String>()

        // 1) 자격
        val elig = eligibilityEngine.evaluate(listing.listingType, core, members, incomes, history)
        val eligScore = when (elig.bestSupplyType) {
            SupplyType.MULTI_CHILD -> 30
            SupplyType.NEWLYWED -> 25
            SupplyType.FIRST_TIME -> 22
            SupplyType.GENERAL -> 12
            null -> 0
        }
        if (elig.bestSupplyType == null) notes += "신청 가능한 유형 없음"

        // 2) 예산
        val budgetScore = budgetFit(units, preferences, notes)

        // 3) 지역
        val regionScore = regionFit(listing.sido, preferences.preferredSidos, notes)

        return MatchingScore(
            eligibility = eligScore,
            budget = budgetScore,
            region = regionScore,
            total = eligScore + budgetScore + regionScore,
            bestSupplyType = elig.bestSupplyType,
            notes = notes,
        )
    }

    /** 가장 저렴한 유닛가가 예산 이내면 만점. 1.0~1.5x 사이는 선형 감소. 1.5x 초과는 0. */
    private fun budgetFit(units: List<ListingUnit>, preferences: Preferences, notes: MutableList<String>): Int {
        val budget = preferences.maxPurchasePrice
        if (budget == null || budget <= 0) {
            notes += "예산 정보 없음 (중간점)"
            return 18
        }
        val cheapest = units.mapNotNull { it.priceMaxKrw }.minOrNull()
        if (cheapest == null) {
            notes += "분양가 정보 없음 (중간점)"
            return 18
        }
        return when {
            cheapest <= budget -> 35
            cheapest <= (budget * 1.5).toLong() -> {
                val ratio = 1.0 - (cheapest - budget).toDouble() / (budget * 0.5)
                (35 * ratio).toInt().coerceAtLeast(0)
            }
            else -> 0
        }
    }

    private fun regionFit(sido: String?, preferred: List<String>, notes: MutableList<String>): Int {
        if (preferred.isEmpty()) {
            notes += "지역 선호 미설정 (중간점)"
            return 17
        }
        if (sido == null) return 0
        val match = preferred.any { sido.startsWith(it.take(2)) || it.startsWith(sido.take(2)) }
        return if (match) 35 else 0
    }
}
