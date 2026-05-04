package app.homefit.domain.profile

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDate

class ScoreCalculatorTest {

    private val today = LocalDate.of(2026, 4, 24)

    @Test
    fun `no profile info gives 0 no-home + minimum dependents 5 + 0 account`() {
        val result = ScoreCalculator.calculate(ProfileCore(), emptyList(), today)
        assertThat(result.noHomePeriod.points).isEqualTo(0)
        assertThat(result.dependents.points).isEqualTo(5)   // 0명도 5점
        assertThat(result.accountAge.points).isEqualTo(0)
        assertThat(result.total).isEqualTo(5)
        assertThat(result.notes.first()).contains("민영주택 가점제")
    }

    @Test
    fun `15+ years no-home + 6 dependents (배우자 + 자녀5) + 15+ year account = max 84`() {
        val core = ProfileCore(
            birthDate = LocalDate.of(1985, 1, 1),
            noHomeSince = today.minusYears(20),
            subscriptionAccountOpenedAt = today.minusYears(20),
        )
        val members = listOf(
            HouseholdMember(relation = HouseholdRelation.SPOUSE, birthDate = LocalDate.of(1986, 1, 1)),
        ) + List(5) { HouseholdMember(relation = HouseholdRelation.CHILD, birthDate = today.minusYears(10)) }
        val result = ScoreCalculator.calculate(core, members, today)
        assertThat(result.total).isEqualTo(ScoreBreakdown.MAX_TOTAL)  // 32 + 35 + 17
    }

    @Test
    fun `married 3 years ago and 30yo today uses later of marriage vs 30yo birthday`() {
        val core = ProfileCore(
            birthDate = LocalDate.of(1996, 4, 24),    // 만 30세 되는 날 = 2026-04-24
            marriageDate = today.minusYears(3),
        )
        val result = ScoreCalculator.calculate(core, emptyList(), today)
        // eligibleStart = max(marriage=2023-04-24, birth+30=2026-04-24) = today → 0년 → 2점
        assertThat(result.noHomePeriod.points).isEqualTo(2)
        assertThat(result.noHomePeriod.reason).contains("만 30세 도달일")
    }

    @Test
    fun `account opened 3 years ago gives 5 points`() {
        val core = ProfileCore(subscriptionAccountOpenedAt = today.minusYears(3))
        val result = ScoreCalculator.calculate(core, emptyList(), today)
        assertThat(result.accountAge.points).isEqualTo(5)
        assertThat(result.accountAge.reason).contains("3년 0개월")
    }

    @Test
    fun `account opened 3 months ago gives 1 point`() {
        val core = ProfileCore(subscriptionAccountOpenedAt = today.minusMonths(3))
        val result = ScoreCalculator.calculate(core, emptyList(), today)
        assertThat(result.accountAge.points).isEqualTo(1)
    }

    // ---- v2: dependents rules ----

    @Test
    fun `child older than 30 is excluded with note`() {
        val members = listOf(
            HouseholdMember(relation = HouseholdRelation.SPOUSE, birthDate = null),
            HouseholdMember(relation = HouseholdRelation.CHILD, birthDate = today.minusYears(35)),
            HouseholdMember(relation = HouseholdRelation.CHILD, birthDate = today.minusYears(10)),
        )
        val result = ScoreCalculator.calculate(ProfileCore(), members, today)
        // 배우자 1 + 30세 미만 자녀 1 = 2명 → 5 + 2*5 = 15
        assertThat(result.dependents.points).isEqualTo(15)
        assertThat(result.dependents.reason).contains("자녀 1명은 만 30세 이상")
    }

    @Test
    fun `parent under 65 is excluded with note`() {
        val members = listOf(
            HouseholdMember(relation = HouseholdRelation.PARENT, birthDate = today.minusYears(60)),
            HouseholdMember(relation = HouseholdRelation.PARENT, birthDate = today.minusYears(70)),
        )
        val result = ScoreCalculator.calculate(ProfileCore(), members, today)
        // 1명만 인정 → 5 + 1*5 = 10
        assertThat(result.dependents.points).isEqualTo(10)
        assertThat(result.dependents.reason).contains("만 65세 미만")
    }

    @Test
    fun `OTHER relation never counts as dependent`() {
        val members = listOf(
            HouseholdMember(relation = HouseholdRelation.OTHER, birthDate = today.minusYears(40)),
            HouseholdMember(relation = HouseholdRelation.OTHER, birthDate = today.minusYears(20)),
        )
        val result = ScoreCalculator.calculate(ProfileCore(), members, today)
        assertThat(result.dependents.points).isEqualTo(5)   // 0명 5점
    }

    @Test
    fun `dependents capped at 6`() {
        val members = listOf(
            HouseholdMember(relation = HouseholdRelation.SPOUSE, birthDate = null),
        ) + List(8) { HouseholdMember(relation = HouseholdRelation.CHILD, birthDate = today.minusYears(5)) }
        val result = ScoreCalculator.calculate(ProfileCore(), members, today)
        assertThat(result.dependents.points).isEqualTo(35)
        assertThat(result.dependents.reason).contains("(6명 캡 적용)")
    }

    @Test
    fun `child without birthDate is treated as under 30 (counts)`() {
        val members = listOf(
            HouseholdMember(relation = HouseholdRelation.CHILD, birthDate = null),
        )
        val result = ScoreCalculator.calculate(ProfileCore(), members, today)
        assertThat(result.dependents.points).isEqualTo(10)
    }

    @Test
    fun `noHomeSince explicitly set takes precedence over age and marriage`() {
        val core = ProfileCore(
            birthDate = LocalDate.of(1985, 1, 1),     // 30세 도달일 = 2015-01-01
            marriageDate = LocalDate.of(2018, 1, 1),
            noHomeSince = LocalDate.of(2020, 1, 1),
        )
        val result = ScoreCalculator.calculate(core, emptyList(), today)
        // 2020-01-01 ~ 2026-04-24 = 6년 3개월 → 2 + 6*2 = 14
        assertThat(result.noHomePeriod.points).isEqualTo(14)
        assertThat(result.noHomePeriod.reason).contains("무주택 기산일 2020-01-01")
    }
}
