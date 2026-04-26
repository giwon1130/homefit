package app.homefit.web.loan

import app.homefit.application.loan.LoanEstimateService
import app.homefit.domain.loan.LoanEstimate
import app.homefit.domain.loan.LoanProductResult
import app.homefit.web.security.CurrentUserId
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

@RestController
@RequestMapping("/api/v1/listings")
class LoanController(
    private val service: LoanEstimateService,
) {
    @GetMapping("/{id}/loan-estimate")
    fun estimate(@CurrentUserId userId: Long, @PathVariable id: Long): LoanEstimateResponse {
        val result = service.estimate(userId, id)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "분양가 정보 없음")
        return LoanEstimateResponse.from(result)
    }
}

data class LoanEstimateResponse(
    val listingPriceKrw: Long,
    val annualIncomeKrw: Long?,
    val products: List<LoanProductDto>,
    val recommended: LoanProductDto?,
    val selfFundingKrw: Long?,
    val notes: List<String>,
) {
    companion object {
        fun from(e: LoanEstimate) = LoanEstimateResponse(
            listingPriceKrw = e.listingPriceKrw,
            annualIncomeKrw = e.annualIncomeKrw,
            products = e.products.map { LoanProductDto.from(it) },
            recommended = e.recommended?.let { LoanProductDto.from(it) },
            selfFundingKrw = e.selfFundingKrw,
            notes = e.notes,
        )
    }
}

data class LoanProductDto(
    val name: String,
    val eligible: Boolean,
    val limitKrw: Long?,
    val reasons: List<String>,
) {
    companion object {
        fun from(p: LoanProductResult) = LoanProductDto(p.name, p.eligible, p.limitKrw, p.reasons)
    }
}
