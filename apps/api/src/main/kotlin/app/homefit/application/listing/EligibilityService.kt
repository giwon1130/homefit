package app.homefit.application.listing

import app.homefit.domain.listing.ListingQueryRepository
import app.homefit.domain.listing.eligibility.EligibilityEngine
import app.homefit.domain.listing.eligibility.EligibilityResult
import app.homefit.domain.profile.ProfileRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class EligibilityService(
    private val listings: ListingQueryRepository,
    private val profileRepo: ProfileRepository,
    private val engine: EligibilityEngine,
) {
    @Transactional(readOnly = true)
    fun evaluate(userId: Long, listingId: Long): EligibilityResult? {
        val detail = listings.findDetail(listingId) ?: return null
        val core = profileRepo.findCore(userId) ?: return EligibilityResult(emptyList(), null, emptyList())
        val members = profileRepo.findHouseholdMembers(userId)
        val incomes = profileRepo.findIncomes(userId)
        val history = profileRepo.findHousingHistory(userId)
        return engine.evaluate(detail.listing.listingType, core, members, incomes, history)
    }
}
