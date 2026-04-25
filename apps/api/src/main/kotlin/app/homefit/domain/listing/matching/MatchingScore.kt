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
import app.homefit.domain.profile.Workplace

/**
 * 매칭 스코어 (총 100점):
 *  - 자격(Eligibility) 25점
 *  - 예산(Budget) 25점
 *  - 지역(Region) 20점
 *  - 통근(Commute) 30점 — 본인+배우자 직장 중 더 가까운 쪽 기준
 *
 * 통근 점수는 외부에서 분 단위 commute 값을 주입받음 (도메인 순수성 유지).
 * null 이면 통근 데이터 없는 것으로 보고 중간점(15) 부여.
 */
data class MatchingScore(
    val eligibility: Int,
    val budget: Int,
    val region: Int,
    val commute: Int,
    val total: Int,
    val bestSupplyType: SupplyType?,
    val commuteMinutes: Int?,
    val notes: List<String>,
) {
    companion object {
        const val MAX_TOTAL = 100
        const val MAX_ELIG = 25
        const val MAX_BUDGET = 25
        const val MAX_REGION = 20
        const val MAX_COMMUTE = 30
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
        workplaces: List<Workplace>,
        commuteLookup: (origin: Pair<java.math.BigDecimal, java.math.BigDecimal>, dest: Pair<java.math.BigDecimal, java.math.BigDecimal>) -> Int? = { _, _ -> null },
    ): MatchingScore {
        val notes = mutableListOf<String>()

        // 1) 자격
        val elig = eligibilityEngine.evaluate(listing.listingType, core, members, incomes, history)
        val eligScore = when (elig.bestSupplyType) {
            SupplyType.MULTI_CHILD -> 25
            SupplyType.NEWLYWED -> 22
            SupplyType.FIRST_TIME -> 20
            SupplyType.GENERAL -> 10
            null -> 0
        }
        if (elig.bestSupplyType == null) notes += "신청 가능한 유형 없음"

        // 2) 예산
        val budgetScore = budgetFit(units, preferences, notes)

        // 3) 지역
        val regionScore = regionFit(listing.sido, preferences.preferredSidos, notes)

        // 4) 통근 — listing 좌표와 사용자 직장 모두 있어야 계산
        val (commuteScore, minutes) = commuteFit(listing, workplaces, commuteLookup, notes)

        return MatchingScore(
            eligibility = eligScore,
            budget = budgetScore,
            region = regionScore,
            commute = commuteScore,
            total = eligScore + budgetScore + regionScore + commuteScore,
            bestSupplyType = elig.bestSupplyType,
            commuteMinutes = minutes,
            notes = notes,
        )
    }

    private fun budgetFit(units: List<ListingUnit>, preferences: Preferences, notes: MutableList<String>): Int {
        val budget = preferences.maxPurchasePrice
        if (budget == null || budget <= 0) { notes += "예산 정보 없음"; return 12 }
        val cheapest = units.mapNotNull { it.priceMaxKrw }.minOrNull()
        if (cheapest == null) { notes += "분양가 정보 없음"; return 12 }
        return when {
            cheapest <= budget -> 25
            cheapest <= (budget * 1.5).toLong() -> {
                val ratio = 1.0 - (cheapest - budget).toDouble() / (budget * 0.5)
                (25 * ratio).toInt().coerceAtLeast(0)
            }
            else -> 0
        }
    }

    private fun regionFit(sido: String?, preferred: List<String>, notes: MutableList<String>): Int {
        if (preferred.isEmpty()) { notes += "지역 선호 미설정"; return 10 }
        if (sido == null) return 0
        val match = preferred.any { sido.startsWith(it.take(2)) || it.startsWith(sido.take(2)) }
        return if (match) 20 else 0
    }

    /**
     * 통근 점수: 본인+배우자 직장 중 가장 짧은 통근시간 기준.
     *  - 30분 이내: 30점
     *  - 30~60분: 30 → 10 선형 감쇠
     *  - 60~90분: 10 → 0 선형 감쇠
     *  - 90분 초과: 0
     *  - 데이터 부족: 15 (중간점)
     */
    private fun commuteFit(
        listing: Listing,
        workplaces: List<Workplace>,
        commuteLookup: (Pair<java.math.BigDecimal, java.math.BigDecimal>, Pair<java.math.BigDecimal, java.math.BigDecimal>) -> Int?,
        notes: MutableList<String>,
    ): Pair<Int, Int?> {
        val lLat = listing.latitude; val lLng = listing.longitude
        if (lLat == null || lLng == null) { notes += "단지 좌표 없음 (통근 중간점)"; return 15 to null }
        val origins = workplaces.mapNotNull { wp ->
            val wLat = wp.latitude; val wLng = wp.longitude
            if (wLat != null && wLng != null) wLat to wLng else null
        }
        if (origins.isEmpty()) { notes += "직장 정보 없음 (통근 중간점)"; return 15 to null }

        val minutes = origins.mapNotNull { commuteLookup(it, lLat to lLng) }.minOrNull()
        if (minutes == null) { notes += "통근시간 조회 실패 (통근 중간점)"; return 15 to null }

        val score = when {
            minutes <= 30 -> 30
            minutes <= 60 -> (30 - 20.0 * (minutes - 30) / 30).toInt()
            minutes <= 90 -> (10 - 10.0 * (minutes - 60) / 30).toInt()
            else -> 0
        }
        return score to minutes
    }
}
