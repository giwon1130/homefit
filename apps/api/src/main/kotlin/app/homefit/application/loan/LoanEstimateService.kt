package app.homefit.application.loan

import app.homefit.domain.listing.ListingQueryRepository
import app.homefit.domain.loan.LoanCalculator
import app.homefit.domain.loan.LoanEstimate
import app.homefit.domain.loan.LoanInputs
import app.homefit.domain.profile.ProfileRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate
import java.time.Period

@Service
class LoanEstimateService(
    private val listings: ListingQueryRepository,
    private val profile: ProfileRepository,
    private val calculator: LoanCalculator,
) {
    @Transactional(readOnly = true)
    fun estimate(userId: Long, listingId: Long): LoanEstimate? {
        val detail = listings.findDetail(listingId) ?: return null

        // 가장 저렴한 유닛가(분양가) 사용. 없으면 추정 불가.
        val cheapest = detail.units.mapNotNull { it.priceMaxKrw }.minOrNull() ?: return null

        val core = profile.findCore(userId)
        val members = profile.findHouseholdMembers(userId)
        val incomes = profile.findIncomes(userId)
        val history = profile.findHousingHistory(userId)
        val assets = profile.findAssets(userId)

        val today = LocalDate.now()
        val annualIncome = latestAnnualIncome(incomes)
        val marriageYears = core?.marriageDate?.let { Period.between(it, today).years }
        val childrenCount = members.count {
            it.relation == app.homefit.domain.profile.HouseholdRelation.CHILD
        }

        val input = LoanInputs(
            listingPriceKrw = cheapest,
            annualIncomeKrw = annualIncome,
            marriageYears = marriageYears,
            isFirstTimeBuyer = core?.isFirstTimeBuyer,
            hasOwnedHouse = history.isNotEmpty(),
            childrenCount = childrenCount,
            region = detail.listing.sido,
            monthlyDebtKrw = assets?.monthlyDebt,
        )
        return calculator.estimate(input)
    }

    private fun latestAnnualIncome(incomes: List<app.homefit.domain.profile.Income>): Long? {
        val latest = incomes.maxByOrNull { it.year } ?: return null
        val total = (latest.selfAmount ?: 0L) + (latest.spouseAmount ?: 0L)
        return if (total > 0) total else null
    }
}
