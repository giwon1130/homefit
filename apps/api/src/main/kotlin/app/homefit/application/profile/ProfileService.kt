package app.homefit.application.profile

import app.homefit.domain.profile.Assets
import app.homefit.domain.profile.FullProfile
import app.homefit.domain.profile.HouseholdMember
import app.homefit.domain.profile.HousingHistory
import app.homefit.domain.profile.Income
import app.homefit.domain.profile.Preferences
import app.homefit.domain.profile.ProfileCore
import app.homefit.domain.profile.ProfileRepository
import app.homefit.domain.profile.Residence
import app.homefit.domain.profile.Workplace
import app.homefit.infrastructure.cache.MatchCache
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ProfileService(
    private val repo: ProfileRepository,
    private val matchCache: MatchCache,
) {
    @Transactional(readOnly = true)
    fun getFullProfile(userId: Long): FullProfile = FullProfile(
        userId = userId,
        core = repo.findCore(userId) ?: ProfileCore(),
        householdMembers = repo.findHouseholdMembers(userId),
        incomes = repo.findIncomes(userId),
        assets = repo.findAssets(userId),
        residences = repo.findResidences(userId),
        workplaces = repo.findWorkplaces(userId),
        preferences = repo.findPreferences(userId),
        housingHistory = repo.findHousingHistory(userId),
    )

    fun saveCore(userId: Long, core: ProfileCore) {
        repo.saveCore(userId, core); matchCache.evict(userId)
    }

    fun replaceHouseholdMembers(userId: Long, members: List<HouseholdMember>) {
        repo.replaceHouseholdMembers(userId, members); matchCache.evict(userId)
    }

    fun replaceIncomes(userId: Long, incomes: List<Income>) {
        repo.replaceIncomes(userId, incomes); matchCache.evict(userId)
    }

    fun saveAssets(userId: Long, assets: Assets) {
        repo.saveAssets(userId, assets); matchCache.evict(userId)
    }

    fun replaceResidences(userId: Long, residences: List<Residence>) {
        repo.replaceResidences(userId, residences); matchCache.evict(userId)
    }

    fun replaceWorkplaces(userId: Long, workplaces: List<Workplace>) {
        repo.replaceWorkplaces(userId, workplaces); matchCache.evict(userId)
    }

    fun savePreferences(userId: Long, preferences: Preferences) {
        repo.savePreferences(userId, preferences); matchCache.evict(userId)
    }

    fun replaceHousingHistory(userId: Long, history: List<HousingHistory>) {
        repo.replaceHousingHistory(userId, history); matchCache.evict(userId)
    }
}
