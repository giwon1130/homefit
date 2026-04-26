package app.homefit.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "homefit.loan")
data class LoanProperties(
    val bogeumjari: BogeumjariRule = BogeumjariRule(),
    val newlywedDidimdol: NewlywedDidimdolRule = NewlywedDidimdolRule(),
    val regionLtv: Map<String, Int> = emptyMap(),    // sido → LTV %
    val defaultLtvPct: Int = 70,
) {
    /** 보금자리론 (HF) — 2025 기준 추정. 매년 갱신 필요. */
    data class BogeumjariRule(
        val incomeLimitDefault: Long = 70_000_000L,        // 부부 연소득 7천 (원)
        val incomeLimitMultiChild: Long = 100_000_000L,    // 다자녀 1억
        val priceLimit: Long = 900_000_000L,                // 주택가격 9억 한도
        val maxLoanDefault: Long = 420_000_000L,            // 한도 4.2억
        val maxLoanFirstTime: Long = 450_000_000L,          // 생애최초 4.5억
    )

    /** 신혼부부 디딤돌 (주택도시기금) — 2025 기준 추정. */
    data class NewlywedDidimdolRule(
        val marriageYearsMax: Int = 7,
        val incomeLimit: Long = 85_000_000L,         // 부부합산 8.5천
        val priceLimit: Long = 600_000_000L,         // 주택가격 6억
        val maxLoan: Long = 400_000_000L,            // 한도 4억
    )
}
