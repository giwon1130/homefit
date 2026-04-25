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
            workplaces = emptyList(),
        )
        // 빈 프로필 → 생애최초 자격으로 FIRST_TIME 우선 (20점)
        assertThat(score.eligibility).isEqualTo(20)
        assertThat(score.budget).isEqualTo(25)
        assertThat(score.region).isEqualTo(20)
        assertThat(score.commute).isEqualTo(15) // 통근 정보 부족 → 중간점
        assertThat(score.total).isEqualTo(80)
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
            workplaces = emptyList(),
        )
        assertThat(score.bestSupplyType?.name).isEqualTo("NEWLYWED")
        assertThat(score.eligibility).isEqualTo(22)
    }

    @Test
    fun `예산 초과시 점수 감소`() {
        val cheap = calculator.calculate(
            listing = listing("서울특별시"),
            units = listOf(unit(500_000_000L)),
            core = ProfileCore(), members = emptyList(), incomes = emptyList(), history = emptyList(),
            preferences = Preferences(maxPurchasePrice = 1_000_000_000L, preferredSidos = listOf("서울")),
            workplaces = emptyList(),
        )
        val over = calculator.calculate(
            listing = listing("서울특별시"),
            units = listOf(unit(2_000_000_000L)),
            core = ProfileCore(), members = emptyList(), incomes = emptyList(), history = emptyList(),
            preferences = Preferences(maxPurchasePrice = 1_000_000_000L, preferredSidos = listOf("서울")),
            workplaces = emptyList(),
        )
        assertThat(cheap.budget).isEqualTo(25)
        assertThat(over.budget).isEqualTo(0)
    }

    @Test
    fun `지역 불일치시 0점`() {
        val score = calculator.calculate(
            listing = listing("부산광역시"),
            units = listOf(unit(500_000_000L)),
            core = ProfileCore(), members = emptyList(), incomes = emptyList(), history = emptyList(),
            preferences = Preferences(maxPurchasePrice = 1_000_000_000L, preferredSidos = listOf("서울")),
            workplaces = emptyList(),
        )
        assertThat(score.region).isEqualTo(0)
    }

    @Test
    fun `통근 30분 이내면 만점 30`() {
        val score = calculator.calculate(
            listing = listing("서울특별시").copy(latitude = BigDecimal("37.5"), longitude = BigDecimal("127.0")),
            units = listOf(unit(500_000_000L)),
            core = ProfileCore(), members = emptyList(), incomes = emptyList(), history = emptyList(),
            preferences = Preferences(maxPurchasePrice = 1_000_000_000L, preferredSidos = listOf("서울")),
            workplaces = listOf(
                app.homefit.domain.profile.Workplace(
                    owner = app.homefit.domain.profile.WorkplaceOwner.SELF,
                    address = "x", latitude = BigDecimal("37.6"), longitude = BigDecimal("127.0"),
                ),
            ),
            commuteLookup = { _, _ -> 25 },  // 25분
        )
        assertThat(score.commute).isEqualTo(30)
        assertThat(score.commuteMinutes).isEqualTo(25)
    }

    @Test
    fun `통근 90분 초과면 0점`() {
        val score = calculator.calculate(
            listing = listing("서울특별시").copy(latitude = BigDecimal("37.5"), longitude = BigDecimal("127.0")),
            units = listOf(unit(500_000_000L)),
            core = ProfileCore(), members = emptyList(), incomes = emptyList(), history = emptyList(),
            preferences = Preferences(),
            workplaces = listOf(
                app.homefit.domain.profile.Workplace(
                    owner = app.homefit.domain.profile.WorkplaceOwner.SELF,
                    address = "x", latitude = BigDecimal("37.0"), longitude = BigDecimal("127.0"),
                ),
            ),
            commuteLookup = { _, _ -> 120 },
        )
        assertThat(score.commute).isEqualTo(0)
    }
}
