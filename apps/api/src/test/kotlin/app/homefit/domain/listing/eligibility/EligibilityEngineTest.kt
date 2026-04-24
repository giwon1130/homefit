package app.homefit.domain.listing.eligibility

import app.homefit.config.EligibilityProperties
import app.homefit.domain.listing.ListingType
import app.homefit.domain.listing.SupplyType
import app.homefit.domain.profile.HouseholdMember
import app.homefit.domain.profile.HouseholdRelation
import app.homefit.domain.profile.HousingHistory
import app.homefit.domain.profile.Income
import app.homefit.domain.profile.ProfileCore
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDate

class EligibilityEngineTest {

    private val today = LocalDate.of(2026, 4, 24)

    private val props = EligibilityProperties(
        urbanWorkerMonthlyIncome = mapOf(1 to 3_600_000L, 2 to 5_400_000L, 3 to 7_200_000L, 4 to 8_200_000L),
        rules = EligibilityProperties.RulesByListingType(
            privateSale = EligibilityProperties.SupplyRules(
                newlywed = EligibilityProperties.NewlywedRule(marriageYearsMax = 7, incomePctLimit = 140),
                firstTime = EligibilityProperties.FirstTimeRule(incomePctLimit = 130, requireNoHome = true),
                multiChild = EligibilityProperties.MultiChildRule(minChildren = 2),
                general = true,
            ),
        ),
    )
    private val engine = EligibilityEngine(props)

    @Test
    fun `no profile gives empty eligibility`() {
        val result = engine.evaluate(
            ListingType.PRIVATE_SALE,
            ProfileCore(), emptyList(), emptyList(), emptyList(), today,
        )
        // 일반공급은 항상 가능
        assertThat(result.eligibleSupplyTypes).contains(SupplyType.GENERAL)
    }

    @Test
    fun `newlywed couple with mid-range income eligible for newlywed special`() {
        // 2인 가구 기준 월소득 100% = 540만, 140% = 756만.
        // 연 8천 = 월 666만 → 123% → 140% 이내.
        val core = ProfileCore(marriageDate = today.minusYears(3))
        val members = listOf(HouseholdMember(relation = HouseholdRelation.SPOUSE, birthDate = null))
        val incomes = listOf(Income(year = 2025, selfAmount = 50_000_000L, spouseAmount = 30_000_000L))
        val result = engine.evaluate(ListingType.PRIVATE_SALE, core, members, incomes, emptyList(), today)
        assertThat(result.eligibleSupplyTypes).contains(SupplyType.NEWLYWED)
        assertThat(result.bestSupplyType).isEqualTo(SupplyType.NEWLYWED)
    }

    @Test
    fun `first time with previous ownership gets rejected`() {
        val core = ProfileCore(isFirstTimeBuyer = true)
        val history = listOf(HousingHistory(ownedFrom = today.minusYears(10), ownedTo = today.minusYears(2)))
        val result = engine.evaluate(ListingType.PRIVATE_SALE, core, emptyList(), emptyList(), history, today)
        val firstTime = result.details.first { it.supplyType == SupplyType.FIRST_TIME }
        assertThat(firstTime.eligible).isFalse()
    }

    @Test
    fun `multi child with 2 children eligible`() {
        val members = listOf(
            HouseholdMember(relation = HouseholdRelation.CHILD, birthDate = null),
            HouseholdMember(relation = HouseholdRelation.CHILD, birthDate = null),
        )
        val result = engine.evaluate(ListingType.PRIVATE_SALE, ProfileCore(), members, emptyList(), emptyList(), today)
        assertThat(result.eligibleSupplyTypes).contains(SupplyType.MULTI_CHILD)
        // best는 multi-child 우선
        assertThat(result.bestSupplyType).isEqualTo(SupplyType.MULTI_CHILD)
    }

    @Test
    fun `high income exceeds newlywed limit`() {
        val core = ProfileCore(marriageDate = today.minusYears(2))
        val members = listOf(HouseholdMember(relation = HouseholdRelation.SPOUSE, birthDate = null))
        // 2인 기준 월 540만원 → 140% = 756만원. 연 1.5억 = 월 1,250만원 → 231%
        val incomes = listOf(Income(year = 2025, selfAmount = 100_000_000L, spouseAmount = 50_000_000L))
        val result = engine.evaluate(ListingType.PRIVATE_SALE, core, members, incomes, emptyList(), today)
        val newlywed = result.details.first { it.supplyType == SupplyType.NEWLYWED }
        assertThat(newlywed.eligible).isFalse()
    }

    @Test
    fun `other listing type returns empty supply rules`() {
        val result = engine.evaluate(ListingType.OTHER, ProfileCore(), emptyList(), emptyList(), emptyList(), today)
        assertThat(result.details).isEmpty()
    }
}
