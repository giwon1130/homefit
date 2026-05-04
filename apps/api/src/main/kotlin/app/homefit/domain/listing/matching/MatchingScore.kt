package app.homefit.domain.listing.matching

import app.homefit.domain.listing.Listing
import app.homefit.domain.listing.ListingUnit
import app.homefit.domain.listing.SupplyType
import app.homefit.domain.listing.eligibility.EligibilityEngine
import app.homefit.domain.profile.Assets
import app.homefit.domain.profile.HouseholdMember
import app.homefit.domain.profile.HousingHistory
import app.homefit.domain.profile.Income
import app.homefit.domain.profile.Preferences
import app.homefit.domain.profile.ProfileCore
import app.homefit.domain.profile.Workplace

/**
 * 매칭 스코어 (총 100점):
 *  - 자격(Eligibility) 25점
 *  - 예산(Budget) 25점 — 사용자 명시 한도 + 실제 자금조달 가능성(자기자본+DSR 대출)
 *  - 지역(Region) 20점
 *  - 통근(Commute) 30점 — 본인+배우자 직장 중 더 가까운 쪽 기준
 *
 * v2 변경:
 *  - budget: "예산 OK이지만 대출 한도 부족" 케이스 잡기 위해 자기자본+DSR 한도 비교 추가.
 *  - region: 시도 풀네임/약식 정규화 (서울특별시 ↔ 서울 ↔ 강원도/강원특별자치도 등).
 *  - notes: 항목별 reason 풍부화 — 점수 산식 명시.
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

/** 시도 풀네임/약식 표 — region 매칭 정답지. */
private val SIDO_PAIRS = listOf(
    "서울특별시" to "서울",
    "경기도" to "경기",
    "인천광역시" to "인천",
    "부산광역시" to "부산",
    "대구광역시" to "대구",
    "대전광역시" to "대전",
    "광주광역시" to "광주",
    "울산광역시" to "울산",
    "세종특별자치시" to "세종",
    "강원특별자치도" to "강원",
    "충청북도" to "충북",
    "충청남도" to "충남",
    "전북특별자치도" to "전북",
    "전라남도" to "전남",
    "경상북도" to "경북",
    "경상남도" to "경남",
    "제주특별자치도" to "제주",
)

/** 입력 시도 표기를 표준 풀네임으로 정규화 (없으면 원본 trim). */
internal fun normalizeSido(s: String?): String? {
    if (s == null) return null
    val trimmed = s.trim()
    if (trimmed.isEmpty()) return null
    SIDO_PAIRS.forEach { (full, short) ->
        if (trimmed == full || trimmed == short) return full
    }
    // 옛 표기 호환: "강원도" → "강원" 매칭, "전라북도" → "전북" 매칭
    val key = trimmed.take(2)
    SIDO_PAIRS.forEach { (full, short) ->
        if (full.startsWith(key) || short == key) return full
    }
    return trimmed
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
        assets: Assets? = null,
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
        notes += if (elig.bestSupplyType != null) {
            "자격: ${elig.bestSupplyType} → ${eligScore}점"
        } else {
            "자격: 신청 가능한 유형 없음 → 0점"
        }

        // 2) 예산
        val budgetScore = budgetFit(units, preferences, incomes, assets, notes)

        // 3) 지역
        val regionScore = regionFit(listing.sido, preferences.preferredSidos, notes)

        // 4) 통근
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

    /**
     * 예산 점수.
     *  - 사용자 명시 maxPurchasePrice 와 cheapest unit 비교 → base
     *  - 자기자본 + DSR 한도로 산출한 actualCapacity 와 cheapest 비교 → 페널티
     *  - actualCapacity < cheapest 인데 maxPurchasePrice >= cheapest 면
     *    "예산 설정은 OK이지만 소득/자산 기준으로는 부족" → -10 + reason.
     */
    private fun budgetFit(
        units: List<ListingUnit>,
        preferences: Preferences,
        incomes: List<Income>,
        assets: Assets?,
        notes: MutableList<String>,
    ): Int {
        val cheapest = units.mapNotNull { it.priceMaxKrw }.minOrNull()
        if (cheapest == null) {
            notes += "예산: 분양가 정보 없음 → 12점 (중립)"
            return 12
        }

        val budget = preferences.maxPurchasePrice
        if (budget == null || budget <= 0) {
            notes += "예산: 사용자 한도 미설정 → 12점 (중립, 분양가 ${cheapest.eokLabel()})"
            return 12
        }
        val baseScore = when {
            cheapest <= budget -> 25
            cheapest <= (budget * 1.5).toLong() -> {
                val ratio = 1.0 - (cheapest - budget).toDouble() / (budget * 0.5)
                (25 * ratio).toInt().coerceAtLeast(0)
            }
            else -> 0
        }

        // DSR 기반 실제 구매가능 금액 검증
        val capacity = financialCapacity(incomes, assets)
        if (capacity != null && capacity < cheapest && budget >= cheapest) {
            val gapEok = (cheapest - capacity).eokLabel()
            notes += "예산: 한도 ${budget.eokLabel()} ≥ 분양가 ${cheapest.eokLabel()} OK이나, " +
                "소득·자산 기준 자금조달 한계 약 ${capacity.eokLabel()} → ${gapEok} 부족, -10점"
            return (baseScore - 10).coerceAtLeast(0)
        }
        val sign = if (cheapest <= budget) "≤" else ">"
        notes += "예산: 한도 ${budget.eokLabel()} $sign 분양가 ${cheapest.eokLabel()} → ${baseScore}점"
        return baseScore
    }

    /** 사용자의 실제 구매가능 금액 추정. 자기자본 + DSR 기준 대출 한도. null=정보 부족. */
    private fun financialCapacity(incomes: List<Income>, assets: Assets?): Long? {
        val income = incomes.maxByOrNull { it.year }
            ?.let { (it.selfAmount ?: 0L) + (it.spouseAmount ?: 0L) }
        if (income == null || income == 0L) return null

        // DSR 40%, 30년 4.5% 기준 1억당 연 6,080,220원 상환 (LoanCalculator 와 동일 가정).
        val dsrAnnualCap = (income * 0.40).toLong()
        val existingAnnualPayment = (assets?.monthlyDebt ?: 0L) * 12
        val available = (dsrAnnualCap - existingAnnualPayment).coerceAtLeast(0)
        val annualPaymentPer1Eok = 6_080_220L
        val maxLoan = (available.toDouble() / annualPaymentPer1Eok * 100_000_000L).toLong()

        val selfFunding = ((assets?.netWorth ?: 0L) - (assets?.realEstate ?: 0L)).coerceAtLeast(0L)
        return selfFunding + maxLoan
    }

    private fun regionFit(sido: String?, preferred: List<String>, notes: MutableList<String>): Int {
        if (preferred.isEmpty()) {
            notes += "지역: 선호 미설정 → 10점 (중립)"
            return 10
        }
        val listingNorm = normalizeSido(sido)
        if (listingNorm == null) {
            notes += "지역: 단지 시도 정보 없음 → 0점"
            return 0
        }
        val matched = preferred.any { normalizeSido(it) == listingNorm }
        return if (matched) {
            notes += "지역: $listingNorm (선호 ✔) → 20점"
            20
        } else {
            notes += "지역: $listingNorm (선호 미일치) → 0점"
            0
        }
    }

    private fun commuteFit(
        listing: Listing,
        workplaces: List<Workplace>,
        commuteLookup: (Pair<java.math.BigDecimal, java.math.BigDecimal>, Pair<java.math.BigDecimal, java.math.BigDecimal>) -> Int?,
        notes: MutableList<String>,
    ): Pair<Int, Int?> {
        val origins = workplaces.mapNotNull { wp ->
            val wLat = wp.latitude; val wLng = wp.longitude
            if (wLat != null && wLng != null) wLat to wLng else null
        }
        if (origins.isEmpty()) {
            notes += "통근: 직장 미입력 → 15점 (중립)"
            return 15 to null
        }
        val lLat = listing.latitude; val lLng = listing.longitude
        if (lLat == null || lLng == null) {
            notes += "통근: 단지 좌표 미확보 → 3점 (페널티)"
            return 3 to null
        }
        val minutes = origins.mapNotNull { commuteLookup(it, lLat to lLng) }.minOrNull()
        if (minutes == null) {
            notes += "통근: 시간 조회 실패 → 5점 (페널티)"
            return 5 to null
        }
        val score = when {
            minutes <= 30 -> 30
            minutes <= 60 -> (30 - 20.0 * (minutes - 30) / 30).toInt()
            minutes <= 90 -> (10 - 10.0 * (minutes - 60) / 30).toInt()
            else -> 0
        }
        notes += "통근: 가장 가까운 직장에서 ${minutes}분 → ${score}점"
        return score to minutes
    }
}

/** 한 줄 노트용 — 1자리 소수점 억 단위 표기. */
private fun Long.eokLabel(): String =
    "%.1f억".format(this / 100_000_000.0)
