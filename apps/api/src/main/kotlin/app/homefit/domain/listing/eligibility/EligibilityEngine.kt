package app.homefit.domain.listing.eligibility

import app.homefit.config.EligibilityProperties
import app.homefit.domain.listing.ListingType
import app.homefit.domain.listing.SupplyType
import app.homefit.domain.profile.HouseholdMember
import app.homefit.domain.profile.HouseholdRelation
import app.homefit.domain.profile.HousingHistory
import app.homefit.domain.profile.Income
import app.homefit.domain.profile.ProfileCore
import java.time.LocalDate
import java.time.Period

data class EligibilityDetail(
    val supplyType: SupplyType,
    val eligible: Boolean,
    val reasons: List<String>,
)

data class EligibilityResult(
    val eligibleSupplyTypes: List<SupplyType>,
    val bestSupplyType: SupplyType?,
    val details: List<EligibilityDetail>,
)

/**
 * 순수 도메인 계산. 입력은 프로필/가족/소득/이력 + 공고 유형. Phase 1 v1은 가구원수 기반
 * 소득 %만 체크. Phase 2에서 공고별 세부 룰(raw_json) 반영.
 */
class EligibilityEngine(
    private val props: EligibilityProperties,
) {
    fun evaluate(
        listingType: ListingType,
        core: ProfileCore,
        members: List<HouseholdMember>,
        incomes: List<Income>,
        history: List<HousingHistory>,
        today: LocalDate = LocalDate.now(),
    ): EligibilityResult {
        val supplyRules = rulesFor(listingType) ?: return EligibilityResult(emptyList(), null, emptyList())

        val hasOwnedHouse = history.isNotEmpty()
        val familyCount = 1 + members.count { it.relation != HouseholdRelation.GRANDPARENT }   // 본인+부양가족 근사
        val childrenCount = members.count { it.relation == HouseholdRelation.CHILD }
        val monthlyIncomePct = monthlyIncomePct(incomes, familyCount, today)

        val details = listOfNotNull(
            supplyRules.newlywed?.let { rule -> evaluateNewlywed(rule, core, monthlyIncomePct, today) },
            supplyRules.firstTime?.let { rule -> evaluateFirstTime(rule, core, hasOwnedHouse, monthlyIncomePct) },
            supplyRules.multiChild?.let { rule -> evaluateMultiChild(rule, childrenCount) },
            if (supplyRules.general) evaluateGeneral() else null,
        )
        val eligible = details.filter { it.eligible }.map { it.supplyType }
        val best = pickBest(eligible)
        return EligibilityResult(eligible, best, details)
    }

    private fun rulesFor(type: ListingType): EligibilityProperties.SupplyRules? = when (type) {
        ListingType.PRIVATE_SALE -> props.rules.privateSale
        ListingType.PUBLIC_SALE -> props.rules.publicSale
        ListingType.NEWLYWED_HOPE -> props.rules.newlywedHope
        ListingType.HAPPY_HOUSE -> props.rules.happyHouse
        else -> null
    }

    private fun evaluateNewlywed(
        rule: EligibilityProperties.NewlywedRule,
        core: ProfileCore,
        incomePct: Int?,
        today: LocalDate,
    ): EligibilityDetail {
        val reasons = mutableListOf<String>()
        val marriage = core.marriageDate
        val marriageYears = marriage?.let { Period.between(it, today).years }

        val marriageOk = marriage != null && marriageYears!! <= rule.marriageYearsMax
        if (marriage == null) reasons += "혼인일 미입력"
        else if (!marriageOk) reasons += "혼인 ${marriageYears}년차 (${rule.marriageYearsMax}년 이내 요건 초과)"
        else reasons += "혼인 ${marriageYears}년차 (${rule.marriageYearsMax}년 이내)"

        val incomeOk = incomePct == null || incomePct <= rule.incomePctLimit
        if (incomePct == null) reasons += "소득 정보 미입력 (보수적으로 가능으로 간주)"
        else if (!incomeOk) reasons += "부부합산 월소득 ${incomePct}% (한도 ${rule.incomePctLimit}%)"
        else reasons += "부부합산 월소득 ${incomePct}% (한도 ${rule.incomePctLimit}% 이내)"

        return EligibilityDetail(SupplyType.NEWLYWED, marriageOk && incomeOk, reasons)
    }

    private fun evaluateFirstTime(
        rule: EligibilityProperties.FirstTimeRule,
        core: ProfileCore,
        hasOwnedHouse: Boolean,
        incomePct: Int?,
    ): EligibilityDetail {
        val reasons = mutableListOf<String>()
        val firstTimeFlag = core.isFirstTimeBuyer ?: true
        val noHome = !hasOwnedHouse

        val flagOk = firstTimeFlag
        val noHomeOk = !rule.requireNoHome || noHome
        val incomeOk = incomePct == null || incomePct <= rule.incomePctLimit

        if (!flagOk) reasons += "생애최초 플래그 해제"
        else reasons += "생애최초 해당"
        if (!noHomeOk) reasons += "과거 주택 소유 이력 있음"
        else reasons += "무주택 이력"
        if (incomePct == null) reasons += "소득 미입력"
        else if (!incomeOk) reasons += "월소득 ${incomePct}% (한도 ${rule.incomePctLimit}%)"
        else reasons += "월소득 ${incomePct}% (한도 ${rule.incomePctLimit}% 이내)"

        return EligibilityDetail(SupplyType.FIRST_TIME, flagOk && noHomeOk && incomeOk, reasons)
    }

    private fun evaluateMultiChild(
        rule: EligibilityProperties.MultiChildRule,
        childrenCount: Int,
    ): EligibilityDetail {
        val ok = childrenCount >= rule.minChildren
        val reason = if (ok) "자녀 ${childrenCount}명 (최소 ${rule.minChildren}명 이상)" else "자녀 ${childrenCount}명 (최소 ${rule.minChildren}명 필요)"
        return EligibilityDetail(SupplyType.MULTI_CHILD, ok, listOf(reason))
    }

    private fun evaluateGeneral(): EligibilityDetail =
        EligibilityDetail(SupplyType.GENERAL, true, listOf("일반공급은 상시 가능"))

    private fun pickBest(eligible: List<SupplyType>): SupplyType? =
        // 당첨 가능성 높은 특공부터
        listOf(SupplyType.MULTI_CHILD, SupplyType.NEWLYWED, SupplyType.FIRST_TIME, SupplyType.GENERAL)
            .firstOrNull { it in eligible }

    /** 최근 연도 소득의 부부합산을 해당 가구원수 기준 %로 환산. 없으면 null. */
    private fun monthlyIncomePct(incomes: List<Income>, familyCount: Int, today: LocalDate): Int? {
        if (incomes.isEmpty()) return null
        val latest = incomes.filter { it.year <= today.year }.maxByOrNull { it.year } ?: return null
        val annual = (latest.selfAmount ?: 0L) + (latest.spouseAmount ?: 0L)
        if (annual == 0L) return null
        val monthly = annual / 12L
        val baseline = props.urbanWorkerMonthlyIncome[familyCount]
            ?: props.urbanWorkerMonthlyIncome[3]
            ?: return null
        return ((monthly.toDouble() / baseline) * 100).toInt()
    }
}
