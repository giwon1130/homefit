package app.homefit.domain.loan

import app.homefit.config.LoanProperties

/**
 * Phase 1 v0 대출 한도 계산기.
 *
 * 대표 상품:
 *  1. 보금자리론 (HF) — 가구 연소득/주택가격 한도 내, 한도 4.2억
 *  2. 신혼부부 디딤돌 — 부부 연소득/혼인기간/주택가격 한도, 한도 4억
 *  3. 일반 (LTV) — 지역별 LTV 비율 × 주택가격
 *
 * 모두 적용 가능하면 한도 큰 상품 추천.
 */
class LoanCalculator(private val props: LoanProperties) {

    fun estimate(input: LoanInputs): LoanEstimate {
        val notes = mutableListOf<String>()

        // 1) 상품별 한도
        val rawProducts = listOfNotNull(
            evalBogeumjari(input, notes),
            evalNewlywedDidimdol(input, notes),
            evalGenericLtv(input),
        )

        // 2) DSR 한도 — 모든 상품에 공통 적용
        val dsrCap = computeDsrLimit(input)
        if (dsrCap != null) {
            notes += "DSR 40% 기준 한도 약 ${dsrCap.eok()}억 (기존 채무 월 ${(input.monthlyDebtKrw ?: 0L).man()}만원 반영)"
        }

        // 3) 각 상품 한도에 DSR cap 적용
        val products = rawProducts.map { p ->
            if (!p.eligible || p.limitKrw == null || dsrCap == null) p
            else if (p.limitKrw <= dsrCap) p
            else p.copy(
                limitKrw = dsrCap,
                reasons = p.reasons + "DSR 한도 ${dsrCap.eok()}억 적용 (원 한도 ${p.limitKrw.eok()}억)",
            )
        }

        val recommended = products
            .filter { it.eligible }
            .maxByOrNull { it.limitKrw ?: 0L }

        val selfFunding = recommended?.limitKrw?.let { input.listingPriceKrw - it }?.coerceAtLeast(0)

        return LoanEstimate(
            listingPriceKrw = input.listingPriceKrw,
            annualIncomeKrw = input.annualIncomeKrw,
            products = products,
            recommended = recommended,
            selfFundingKrw = selfFunding,
            notes = notes,
        )
    }

    /**
     * DSR 40% 기준 신규 주담대 가능 한도.
     * 가정: 30년 만기 원리금균등, 연 4.5% → 1억당 월 506,685원 (연 6,080,220원).
     * 대출 한도 ≈ (연소득 × 0.4 - 기존 연 상환) ÷ (연 상환 per 1억) × 1억.
     */
    private fun computeDsrLimit(input: LoanInputs): Long? {
        val income = input.annualIncomeKrw ?: return null
        if (income <= 0) return null
        val dsrAnnualCap = (income * 0.40).toLong()
        val existingAnnualPayment = (input.monthlyDebtKrw ?: 0L) * 12
        val available = dsrAnnualCap - existingAnnualPayment
        if (available <= 0) return 0L
        val annualPaymentPer1Eok = 6_080_220L
        return (available.toDouble() / annualPaymentPer1Eok * 100_000_000L).toLong()
    }

    private fun Long.man(): String =
        (this / 10_000).toString()

    private fun evalBogeumjari(input: LoanInputs, notes: MutableList<String>): LoanProductResult {
        val rule = props.bogeumjari
        val reasons = mutableListOf<String>()
        var ok = true

        // 가구 연소득
        val incomeLimit = if (input.childrenCount >= 2) rule.incomeLimitMultiChild else rule.incomeLimitDefault
        if (input.annualIncomeKrw == null) {
            reasons += "연소득 미입력"
            ok = false
        } else if (input.annualIncomeKrw > incomeLimit) {
            reasons += "연소득 ${incomeLimit.eok()}억 초과 (입력 ${input.annualIncomeKrw.eok()}억)"
            ok = false
        } else {
            reasons += "연소득 ${input.annualIncomeKrw.eok()}억 (한도 ${incomeLimit.eok()}억 이내)"
        }

        // 주택가격
        if (input.listingPriceKrw > rule.priceLimit) {
            reasons += "주택가격 ${input.listingPriceKrw.eok()}억 (보금자리론 한도 ${rule.priceLimit.eok()}억 초과)"
            ok = false
        }

        val cap = if (input.isFirstTimeBuyer == true) rule.maxLoanFirstTime else rule.maxLoanDefault
        val limit = if (ok) minOf(cap, (input.listingPriceKrw * 0.7).toLong()) else null
        if (ok) reasons += "한도 ${cap.eok()}억"

        return LoanProductResult("보금자리론", ok, limit, reasons)
    }

    private fun evalNewlywedDidimdol(input: LoanInputs, notes: MutableList<String>): LoanProductResult {
        val rule = props.newlywedDidimdol
        val reasons = mutableListOf<String>()
        var ok = true

        // 혼인 7년 이내
        if (input.marriageYears == null) {
            reasons += "혼인일 미입력 (신혼 자격 판정 불가)"
            ok = false
        } else if (input.marriageYears > rule.marriageYearsMax) {
            reasons += "혼인 ${input.marriageYears}년차 (${rule.marriageYearsMax}년 이내 요건 초과)"
            ok = false
        } else {
            reasons += "혼인 ${input.marriageYears}년차"
        }

        // 무주택
        if (input.hasOwnedHouse) {
            reasons += "무주택 요건 미충족"
            ok = false
        } else {
            reasons += "무주택"
        }

        // 부부합산 연소득
        if (input.annualIncomeKrw == null) {
            reasons += "연소득 미입력"
            ok = false
        } else if (input.annualIncomeKrw > rule.incomeLimit) {
            reasons += "부부합산 연소득 ${rule.incomeLimit.eok()}억 초과 (입력 ${input.annualIncomeKrw.eok()}억)"
            ok = false
        } else {
            reasons += "부부합산 연소득 ${input.annualIncomeKrw.eok()}억"
        }

        // 주택가격
        if (input.listingPriceKrw > rule.priceLimit) {
            reasons += "주택가격 ${input.listingPriceKrw.eok()}억 (디딤돌 한도 ${rule.priceLimit.eok()}억 초과)"
            ok = false
        }

        val limit = if (ok) minOf(rule.maxLoan, (input.listingPriceKrw * 0.8).toLong()) else null
        if (ok) reasons += "한도 ${rule.maxLoan.eok()}억"

        return LoanProductResult("신혼부부 디딤돌", ok, limit, reasons)
    }

    private fun evalGenericLtv(input: LoanInputs): LoanProductResult {
        val ltv = props.regionLtv[input.region] ?: props.defaultLtvPct
        val limit = (input.listingPriceKrw * ltv / 100).toLong()
        val reasons = listOf(
            "지역 ${input.region ?: "미지정"} → LTV ${ltv}%",
            "한도 = 주택가격 × LTV = ${limit.eok()}억",
        )
        return LoanProductResult("일반 (LTV)", true, limit, reasons)
    }

    private fun Long.eok(): String {
        val eok = this / 100_000_000.0
        return "%.1f".format(eok)
    }
}
