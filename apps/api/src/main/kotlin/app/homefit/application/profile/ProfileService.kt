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
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ProfileService(
    private val repo: ProfileRepository,
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

    fun saveCore(userId: Long, core: ProfileCore) = repo.saveCore(userId, core)

    fun replaceHouseholdMembers(userId: Long, members: List<HouseholdMember>) =
        repo.replaceHouseholdMembers(userId, members)

    fun replaceIncomes(userId: Long, incomes: List<Income>) = repo.replaceIncomes(userId, incomes)

    fun saveAssets(userId: Long, assets: Assets) = repo.saveAssets(userId, assets)

    fun replaceResidences(userId: Long, residences: List<Residence>) =
        repo.replaceResidences(userId, residences)

    fun replaceWorkplaces(userId: Long, workplaces: List<Workplace>) =
        repo.replaceWorkplaces(userId, workplaces)

    fun savePreferences(userId: Long, preferences: Preferences) =
        repo.savePreferences(userId, preferences)

    fun replaceHousingHistory(userId: Long, history: List<HousingHistory>) =
        repo.replaceHousingHistory(userId, history)
}
