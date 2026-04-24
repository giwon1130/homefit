package app.homefit.config

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * 자격 판정 엔진 v1 설정. 숫자 기준은 매년 변하므로 application.yml에서 튜닝.
 */
@ConfigurationProperties(prefix = "homefit.eligibility")
data class EligibilityProperties(
    /** 도시근로자 월평균소득 (원). 가구원수 → 월소득 100% 기준. */
    val urbanWorkerMonthlyIncome: Map<Int, Long> = emptyMap(),
    val rules: RulesByListingType = RulesByListingType(),
) {
    data class RulesByListingType(
        val privateSale: SupplyRules = SupplyRules(),
        val publicSale: SupplyRules = SupplyRules(),
        val newlywedHope: SupplyRules = SupplyRules(),
        val happyHouse: SupplyRules = SupplyRules(),
    )

    data class SupplyRules(
        val newlywed: NewlywedRule? = null,
        val firstTime: FirstTimeRule? = null,
        val multiChild: MultiChildRule? = null,
        /** 일반 공급이 존재하는지 (임대 전용은 일반공급 없음). */
        val general: Boolean = true,
    )

    data class NewlywedRule(
        val marriageYearsMax: Int = 7,
        val incomePctLimit: Int = 140,
    )

    data class FirstTimeRule(
        val incomePctLimit: Int = 130,
        val requireNoHome: Boolean = true,
    )

    data class MultiChildRule(
        val minChildren: Int = 2,
    )
}
