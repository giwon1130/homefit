package app.homefit.domain.profile

interface ProfileRepository {
    fun findCore(userId: Long): ProfileCore?
    fun saveCore(userId: Long, core: ProfileCore)

    fun findHouseholdMembers(userId: Long): List<HouseholdMember>
    fun replaceHouseholdMembers(userId: Long, members: List<HouseholdMember>)

    fun findIncomes(userId: Long): List<Income>
    fun replaceIncomes(userId: Long, incomes: List<Income>)

    fun findAssets(userId: Long): Assets?
    fun saveAssets(userId: Long, assets: Assets)

    fun findResidences(userId: Long): List<Residence>
    fun replaceResidences(userId: Long, residences: List<Residence>)

    fun findWorkplaces(userId: Long): List<Workplace>
    fun replaceWorkplaces(userId: Long, workplaces: List<Workplace>)

    fun findPreferences(userId: Long): Preferences?
    fun savePreferences(userId: Long, preferences: Preferences)

    fun findHousingHistory(userId: Long): List<HousingHistory>
    fun replaceHousingHistory(userId: Long, history: List<HousingHistory>)
}
