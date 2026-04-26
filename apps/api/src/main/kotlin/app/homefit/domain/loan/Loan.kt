package app.homefit.domain.loan

/**
 * 대출 한도 추정 — 대표 상품 3종 비교.
 *
 * 한도/소득기준은 매년 갱신되므로 application.yml(homefit.loan) 에서 튜닝.
 * 정확한 신청은 각 기관(주택도시기금/HF) 시뮬레이터로 확인 필요.
 */
data class LoanEstimate(
    val listingPriceKrw: Long,
    val annualIncomeKrw: Long?,
    val products: List<LoanProductResult>,
    val recommended: LoanProductResult?,
    val selfFundingKrw: Long?,
    val notes: List<String>,
)

data class LoanProductResult(
    val name: String,
    val eligible: Boolean,
    val limitKrw: Long?,        // 대출 가능 한도 (eligible=true 일 때만 의미)
    val reasons: List<String>,  // 각 조건 결과 (한도 산정 또는 거절 사유)
)

data class LoanInputs(
    val listingPriceKrw: Long,
    val annualIncomeKrw: Long?,         // 부부합산 연소득
    val marriageYears: Int?,            // 혼인 경과 햇수 (혼인 신청일 기준)
    val isFirstTimeBuyer: Boolean?,
    val hasOwnedHouse: Boolean,
    val childrenCount: Int,
    val region: String?,                // sido — LTV 분기용
    val monthlyDebtKrw: Long? = null,   // 기존 채무 월 상환액 (DSR 계산용)
)
