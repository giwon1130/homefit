package app.homefit.domain.profile

import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime

enum class HouseholdRelation { SPOUSE, CHILD, PARENT, GRANDPARENT, OTHER }

enum class WorkplaceOwner { SELF, SPOUSE }

data class HouseholdMember(
    val id: Long? = null,
    val relation: HouseholdRelation,
    val birthDate: LocalDate?,
)

data class Income(
    val year: Int,
    val selfAmount: Long?,
    val spouseAmount: Long?,
)

data class Assets(
    val netWorth: Long?,
    val realEstate: Long?,
    /** 기존 채무의 월 상환액(원). DSR 계산용. */
    val monthlyDebt: Long? = null,
    val updatedAt: OffsetDateTime? = null,
)

data class Residence(
    val id: Long? = null,
    val address: String,
    val sido: String? = null,
    val sigungu: String? = null,
    val dongCode: String? = null,
    val latitude: BigDecimal? = null,
    val longitude: BigDecimal? = null,
    val livedSince: LocalDate? = null,
    val isCurrent: Boolean = true,
)

data class Workplace(
    val id: Long? = null,
    val owner: WorkplaceOwner,
    val label: String? = null,
    val address: String,
    val latitude: BigDecimal? = null,
    val longitude: BigDecimal? = null,
    val arrivalTime: LocalTime = LocalTime.of(9, 0),
)

data class Preferences(
    val maxPurchasePrice: Long? = null,
    val maxJeonsePrice: Long? = null,
    val maxMonthlyRent: Int? = null,
    val maxDepositForRent: Long? = null,
    val minSizeM2: BigDecimal? = null,
    val maxSizeM2: BigDecimal? = null,
    val minRooms: Int? = null,
    val maxCommuteMinutes: Int? = null,
    val preferredSidos: List<String> = emptyList(),
)

data class HousingHistory(
    val id: Long? = null,
    val ownedFrom: LocalDate,
    val ownedTo: LocalDate?,
    val note: String? = null,
)

/** 프로필 코어 (users 테이블과 1:1) */
data class ProfileCore(
    val birthDate: LocalDate? = null,
    val marriageDate: LocalDate? = null,
    val isHouseholder: Boolean? = null,
    val isFirstTimeBuyer: Boolean? = null,
    val noHomeSince: LocalDate? = null,
    val subscriptionAccountOpenedAt: LocalDate? = null,
    val subscriptionDepositMonths: Int? = null,
    val subscriptionDepositTotal: Long? = null,
)

/** 전체 프로필 애그리게이트 (조회용) */
data class FullProfile(
    val userId: Long,
    val core: ProfileCore,
    val householdMembers: List<HouseholdMember>,
    val incomes: List<Income>,
    val assets: Assets?,
    val residences: List<Residence>,
    val workplaces: List<Workplace>,
    val preferences: Preferences?,
    val housingHistory: List<HousingHistory>,
)
