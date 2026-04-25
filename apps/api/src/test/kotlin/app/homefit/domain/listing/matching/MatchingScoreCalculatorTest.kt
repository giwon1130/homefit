package app.homefit.domain.listing.matching

import app.homefit.config.EligibilityProperties
import app.homefit.domain.listing.Listing
import app.homefit.domain.listing.ListingType
import app.homefit.domain.listing.ListingUnit
import app.homefit.domain.listing.eligibility.EligibilityEngine
import app.homefit.domain.profile.HouseholdMember
import app.homefit.domain.profile.HouseholdRelation
import app.homefit.domain.profile.Income
import app.homefit.domain.profile.Preferences
import app.homefit.domain.profile.ProfileCore
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate

class MatchingScoreCalculatorTest {

    private val today = LocalDate.of(2026, 4, 24)

    private val eligProps = EligibilityProperties(
        urbanWorkerMonthlyIncome = mapOf(2 to 5_400_000L, 3 to 7_200_000L),
        rules = EligibilityProperties.RulesByListingType(
            privateSale = EligibilityProperties.SupplyRules(
                newlywed = EligibilityProperties.NewlywedRule(7, 140),
                firstTime = EligibilityProperties.FirstTimeRule(130, true),
                multiChild = EligibilityProperties.MultiChildRule(2),
                general = true,
            ),
        ),
    )
    private val calculator = MatchingScoreCalculator(EligibilityEngine(eligProps))

    private fun listing(sido: String? = "서울특별시"): Listing = Listing(
        id = 1, source = "x", sourceRef = "1", listingType = ListingType.PRIVATE_SALE,
        name = "n", developer = null, sido = sido, sigungu = null, address = null,
        latitude = null, longitude = null,
        applicationStart = null, applicationEnd = null, announcementDate = null,
        winnerAnnouncementDate = null, contractStartDate = null, contractEndDate = null,
        moveInDate = null, totalSupply = null, rawDocumentUrl = null,
    )

    private fun unit(price: Long): ListingUnit = ListingUnit(
        id = 1, listingId = 1, modelNo = null, unitType = "84A",
        sizeM2 = BigDecimal("84.00"), supplyCount = 100, priceMaxKrw = price,
    )

    @Test
    fun `perfect match - 빈 프로필이지만 자격 예산 지역 다 충족`() {
        val score = calculator.calculate(
            listing = listing("서울특별시"),
            units = listOf(unit(800_000_000L)),
            core = ProfileCore(),
            members = emptyList(),
            incomes = emptyList(),
            history = emptyList(),
            preferences = Preferences(maxPurchasePrice = 1_000_000_000L, preferredSidos = listOf("서울")),
        )
        // 빈 프로필 → 생애최초 자격으로 FIRST_TIME 우선 (22점)
        assertThat(score.eligibility).isEqualTo(22)
        assertThat(score.budget).isEqualTo(35)
        assertThat(score.region).isEqualTo(35)
        assertThat(score.total).isEqualTo(92)
    }

    @Test
    fun `신혼부부 자격 가산`() {
        val score = calculator.calculate(
            listing = listing("서울특별시"),
            units = listOf(unit(700_000_000L)),
            core = ProfileCore(marriageDate = today.minusYears(2)),
            members = listOf(HouseholdMember(relation = HouseholdRelation.SPOUSE, birthDate = null)),
            incomes = listOf(Income(2025, 50_000_000L, 30_000_000L)),
            history = emptyList(),
            preferences = Preferences(maxPurchasePrice = 1_000_000_000L, preferredSidos = listOf("서울")),
        )
        assertThat(score.bestSupplyType?.name).isEqualTo("NEWLYWED")
        assertThat(score.eligibility).isEqualTo(25)
        assertThat(score.total).isEqualTo(25 + 35 + 35)
    }

    @Test
    fun `예산 초과시 점수 감소`() {
        val cheap = calculator.calculate(
            listing = listing("서울특별시"),
            units = listOf(unit(500_000_000L)),  // 예산 내
            core = ProfileCore(), members = emptyList(), incomes = emptyList(), history = emptyList(),
            preferences = Preferences(maxPurchasePrice = 1_000_000_000L, preferredSidos = listOf("서울")),
        )
        val over = calculator.calculate(
            listing = listing("서울특별시"),
            units = listOf(unit(2_000_000_000L)),  // 2배 → 0
            core = ProfileCore(), members = emptyList(), incomes = emptyList(), history = emptyList(),
            preferences = Preferences(maxPurchasePrice = 1_000_000_000L, preferredSidos = listOf("서울")),
        )
        assertThat(cheap.budget).isEqualTo(35)
        assertThat(over.budget).isEqualTo(0)
    }

    @Test
    fun `지역 불일치시 0점`() {
        val score = calculator.calculate(
            listing = listing("부산광역시"),
            units = listOf(unit(500_000_000L)),
            core = ProfileCore(), members = emptyList(), incomes = emptyList(), history = emptyList(),
            preferences = Preferences(maxPurchasePrice = 1_000_000_000L, preferredSidos = listOf("서울")),
        )
        assertThat(score.region).isEqualTo(0)
    }

    @Test
    fun `선호지역 미설정시 중간점`() {
        val score = calculator.calculate(
            listing = listing("부산광역시"),
            units = listOf(unit(500_000_000L)),
            core = ProfileCore(), members = emptyList(), incomes = emptyList(), history = emptyList(),
            preferences = Preferences(maxPurchasePrice = 1_000_000_000L),
        )
        assertThat(score.region).isEqualTo(17)
    }
}
