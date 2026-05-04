package app.homefit.web.profile

import app.homefit.domain.profile.Assets
import app.homefit.domain.profile.FullProfile
import app.homefit.domain.profile.HouseholdMember
import app.homefit.domain.profile.HouseholdRelation
import app.homefit.domain.profile.HousingHistory
import app.homefit.domain.profile.Income
import app.homefit.domain.profile.Preferences
import app.homefit.domain.profile.ProfileCore
import app.homefit.domain.profile.Residence
import app.homefit.domain.profile.ScoreBreakdown
import app.homefit.domain.profile.Workplace
import app.homefit.domain.profile.WorkplaceOwner
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime

data class ProfileCoreDto(
    val birthDate: LocalDate? = null,
    val marriageDate: LocalDate? = null,
    val isHouseholder: Boolean? = null,
    val isFirstTimeBuyer: Boolean? = null,
    val noHomeSince: LocalDate? = null,
    val subscriptionAccountOpenedAt: LocalDate? = null,
    val subscriptionDepositMonths: Int? = null,
    val subscriptionDepositTotal: Long? = null,
) {
    fun toDomain() = ProfileCore(
        birthDate, marriageDate, isHouseholder, isFirstTimeBuyer,
        noHomeSince, subscriptionAccountOpenedAt,
        subscriptionDepositMonths, subscriptionDepositTotal,
    )

    companion object {
        fun from(c: ProfileCore) = ProfileCoreDto(
            c.birthDate, c.marriageDate, c.isHouseholder, c.isFirstTimeBuyer,
            c.noHomeSince, c.subscriptionAccountOpenedAt,
            c.subscriptionDepositMonths, c.subscriptionDepositTotal,
        )
    }
}

data class HouseholdMemberDto(
    @field:NotNull
    val relation: HouseholdRelation,
    val birthDate: LocalDate? = null,
) {
    fun toDomain() = HouseholdMember(relation = relation, birthDate = birthDate)

    companion object {
        fun from(m: HouseholdMember) = HouseholdMemberDto(m.relation, m.birthDate)
    }
}

data class IncomeDto(
    @field:NotNull val year: Int,
    val selfAmount: Long? = null,
    val spouseAmount: Long? = null,
) {
    fun toDomain() = Income(year, selfAmount, spouseAmount)

    companion object {
        fun from(i: Income) = IncomeDto(i.year, i.selfAmount, i.spouseAmount)
    }
}

data class AssetsDto(
    val netWorth: Long? = null,
    val realEstate: Long? = null,
) {
    fun toDomain() = Assets(netWorth, realEstate)

    companion object {
        fun from(a: Assets) = AssetsDto(a.netWorth, a.realEstate)
    }
}

data class ResidenceDto(
    @field:NotBlank val address: String,
    val sido: String? = null,
    val sigungu: String? = null,
    val dongCode: String? = null,
    val latitude: BigDecimal? = null,
    val longitude: BigDecimal? = null,
    val livedSince: LocalDate? = null,
    val isCurrent: Boolean = true,
) {
    fun toDomain() = Residence(null, address, sido, sigungu, dongCode, latitude, longitude, livedSince, isCurrent)

    companion object {
        fun from(r: Residence) = ResidenceDto(r.address, r.sido, r.sigungu, r.dongCode, r.latitude, r.longitude, r.livedSince, r.isCurrent)
    }
}

data class WorkplaceDto(
    @field:NotNull val owner: WorkplaceOwner,
    val label: String? = null,
    @field:NotBlank val address: String,
    val latitude: BigDecimal? = null,
    val longitude: BigDecimal? = null,
    val arrivalTime: LocalTime = LocalTime.of(9, 0),
) {
    fun toDomain() = Workplace(null, owner, label, address, latitude, longitude, arrivalTime)

    companion object {
        fun from(w: Workplace) = WorkplaceDto(w.owner, w.label, w.address, w.latitude, w.longitude, w.arrivalTime)
    }
}

data class PreferencesDto(
    val maxPurchasePrice: Long? = null,
    val maxJeonsePrice: Long? = null,
    val maxMonthlyRent: Int? = null,
    val maxDepositForRent: Long? = null,
    val minSizeM2: BigDecimal? = null,
    val maxSizeM2: BigDecimal? = null,
    val minRooms: Int? = null,
    val maxCommuteMinutes: Int? = null,
    val preferredSidos: List<String> = emptyList(),
) {
    fun toDomain() = Preferences(
        maxPurchasePrice, maxJeonsePrice, maxMonthlyRent, maxDepositForRent,
        minSizeM2, maxSizeM2, minRooms, maxCommuteMinutes, preferredSidos,
    )

    companion object {
        fun from(p: Preferences) = PreferencesDto(
            p.maxPurchasePrice, p.maxJeonsePrice, p.maxMonthlyRent, p.maxDepositForRent,
            p.minSizeM2, p.maxSizeM2, p.minRooms, p.maxCommuteMinutes, p.preferredSidos,
        )
    }
}

data class HousingHistoryDto(
    @field:NotNull val ownedFrom: LocalDate,
    val ownedTo: LocalDate? = null,
    val note: String? = null,
) {
    fun toDomain() = HousingHistory(null, ownedFrom, ownedTo, note)

    companion object {
        fun from(h: HousingHistory) = HousingHistoryDto(h.ownedFrom, h.ownedTo, h.note)
    }
}

data class FullProfileResponse(
    val userId: Long,
    val core: ProfileCoreDto,
    val householdMembers: List<HouseholdMemberDto>,
    val incomes: List<IncomeDto>,
    val assets: AssetsDto?,
    val residences: List<ResidenceDto>,
    val workplaces: List<WorkplaceDto>,
    val preferences: PreferencesDto?,
    val housingHistory: List<HousingHistoryDto>,
) {
    companion object {
        fun from(p: FullProfile) = FullProfileResponse(
            userId = p.userId,
            core = ProfileCoreDto.from(p.core),
            householdMembers = p.householdMembers.map { HouseholdMemberDto.from(it) },
            incomes = p.incomes.map { IncomeDto.from(it) },
            assets = p.assets?.let { AssetsDto.from(it) },
            residences = p.residences.map { ResidenceDto.from(it) },
            workplaces = p.workplaces.map { WorkplaceDto.from(it) },
            preferences = p.preferences?.let { PreferencesDto.from(it) },
            housingHistory = p.housingHistory.map { HousingHistoryDto.from(it) },
        )
    }
}

data class ScoreResponse(
    val total: Int,
    val max: Int,
    val breakdown: BreakdownDto,
    val notes: List<String>,
) {
    data class BreakdownDto(
        val noHomePeriod: Item,
        val dependents: Item,
        val accountAge: Item,
    )

    data class Item(val points: Int, val max: Int, val reason: String)

    companion object {
        fun from(s: ScoreBreakdown) = ScoreResponse(
            total = s.total,
            max = ScoreBreakdown.MAX_TOTAL,
            breakdown = BreakdownDto(
                noHomePeriod = Item(s.noHomePeriod.points, s.noHomePeriod.max, s.noHomePeriod.reason),
                dependents = Item(s.dependents.points, s.dependents.max, s.dependents.reason),
                accountAge = Item(s.accountAge.points, s.accountAge.max, s.accountAge.reason),
            ),
            notes = s.notes,
        )
    }
}
