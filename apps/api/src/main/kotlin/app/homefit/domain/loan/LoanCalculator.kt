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

        val products = listOfNotNull(
            evalBogeumjari(input, notes),
            evalNewlywedDidimdol(input, notes),
            evalGenericLtv(input),
        )

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
