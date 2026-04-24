package app.homefit.domain.profile

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.LocalDate

class ScoreCalculatorTest {

    private val today = LocalDate.of(2026, 4, 24)

    @Test
    fun `no profile info gives 0 no-home but still minimum dependents + 0 account`() {
        val result = ScoreCalculator.calculate(ProfileCore(), emptyList(), today)
        assertThat(result.noHomePeriodPoints).isEqualTo(0)
        assertThat(result.dependentsPoints).isEqualTo(5)   // 0명도 5점
        assertThat(result.accountAgePoints).isEqualTo(0)
        assertThat(result.total).isEqualTo(5)
    }

    @Test
    fun `15+ years no-home + 6 dependents + 15+ year account = max 84`() {
        val core = ProfileCore(
            birthDate = LocalDate.of(1985, 1, 1),
            noHomeSince = today.minusYears(20),
            subscriptionAccountOpenedAt = today.minusYears(20),
        )
        val members = List(6) { HouseholdMember(relation = HouseholdRelation.CHILD, birthDate = null) }
        val result = ScoreCalculator.calculate(core, members, today)
        assertThat(result.total).isEqualTo(ScoreBreakdown.MAX_TOTAL)  // 32 + 35 + 17
    }

    @Test
    fun `married 3 years ago and 30yo but no explicit no_home_since uses later of marriage vs 30yo birthday`() {
        val core = ProfileCore(
            birthDate = LocalDate.of(1996, 4, 24),    // 만 30세 되는 날 = 2026-04-24
            marriageDate = today.minusYears(3),
        )
        val result = ScoreCalculator.calculate(core, emptyList(), today)
        // eligibleStart = max(marriage=2023-04-24, birth+30=2026-04-24) = today
        // years 0 → 2점
        assertThat(result.noHomePeriodPoints).isEqualTo(2)
    }

    @Test
    fun `account opened 3 years ago gives 5 points`() {
        val core = ProfileCore(subscriptionAccountOpenedAt = today.minusYears(3))
        val result = ScoreCalculator.calculate(core, emptyList(), today)
        // months = 36 → 2 + 3 = 5
        assertThat(result.accountAgePoints).isEqualTo(5)
    }

    @Test
    fun `account opened 3 months ago gives 1 point`() {
        val core = ProfileCore(subscriptionAccountOpenedAt = today.minusMonths(3))
        val result = ScoreCalculator.calculate(core, emptyList(), today)
        assertThat(result.accountAgePoints).isEqualTo(1)
    }
}
