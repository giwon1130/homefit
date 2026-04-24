package app.homefit.domain.profile

import java.time.LocalDate
import java.time.Period

/**
 * 민영주택 청약 가점 계산 (국토교통부 고시 기준, 84점 만점).
 *
 * 무주택기간 (32점) + 부양가족 수 (35점) + 청약통장 가입기간 (17점)
 *
 * 정밀 룰:
 *  - 무주택기간: 만 30세부터(혼인 전이면) or 혼인신고일부터 기산. 1년 미만 2점, 1년 단위로 2점씩 가산, 15년 이상 32점.
 *  - 부양가족: 세대주 본인 제외. 없음 5점, 1명 10점, ..., 6명 이상 35점.
 *    ※ 본 구현은 household_members 테이블의 항목 수를 부양가족 수로 간주. 배우자 포함.
 *  - 청약통장: 가입 6개월 미만 1점, 6개월~1년 2점, ..., 15년 이상 17점.
 *
 * 세부 해석은 최신 공고 기준으로 재검토 필요 (2026-04-24 시점).
 */
data class ScoreBreakdown(
    val noHomePeriodPoints: Int,
    val dependentsPoints: Int,
    val accountAgePoints: Int,
    val total: Int,
    val notes: List<String> = emptyList(),
) {
    companion object {
        const val MAX_TOTAL = 84
        const val MAX_NO_HOME = 32
        const val MAX_DEPENDENTS = 35
        const val MAX_ACCOUNT = 17
    }
}

object ScoreCalculator {

    fun calculate(
        core: ProfileCore,
        householdMembers: List<HouseholdMember>,
        today: LocalDate = LocalDate.now(),
    ): ScoreBreakdown {
        val notes = mutableListOf<String>()

        val noHome = noHomePeriodPoints(core, today, notes)
        val deps = dependentsPoints(householdMembers.size, notes)
        val acct = accountAgePoints(core.subscriptionAccountOpenedAt, today, notes)

        return ScoreBreakdown(noHome, deps, acct, noHome + deps + acct, notes)
    }

    /** 무주택기간 포인트. 기산일 없으면 0. */
    private fun noHomePeriodPoints(core: ProfileCore, today: LocalDate, notes: MutableList<String>): Int {
        // 기산 시작일: no_home_since 우선, 없으면 (혼인일 vs 만 30세 되는 날) 중 늦은 것
        val eligibleStart = core.noHomeSince
            ?: listOfNotNull(
                core.marriageDate,
                core.birthDate?.plusYears(30),
            ).maxOrNull()

        if (eligibleStart == null) {
            notes.add("무주택 기간 기산일 정보 부족")
            return 0
        }
        if (eligibleStart.isAfter(today)) {
            notes.add("아직 무주택기간 기산 시점 이전")
            return 0
        }

        val years = Period.between(eligibleStart, today).years
        return when {
            years < 1 -> 2
            years >= 15 -> 32
            else -> 2 + years * 2  // 1년 4점, 2년 6점, ..., 14년 30점
        }
    }

    /** 부양가족 수 포인트. 본인 제외 가족 구성원 수 기준 (0~6명+). */
    private fun dependentsPoints(memberCount: Int, notes: MutableList<String>): Int {
        val clamped = memberCount.coerceIn(0, 6)
        if (memberCount > 6) notes.add("부양가족 6명 초과는 35점 캡")
        return 5 + clamped * 5  // 0명 5점, 1명 10점, ..., 6명 35점
    }

    /** 청약통장 가입기간 포인트. */
    private fun accountAgePoints(openedAt: LocalDate?, today: LocalDate, notes: MutableList<String>): Int {
        if (openedAt == null) {
            notes.add("청약통장 가입일 미입력")
            return 0
        }
        val months = Period.between(openedAt, today).toTotalMonths()
        return when {
            months < 6 -> 1
            months < 12 -> 2
            months >= 15L * 12 -> 17
            else -> 2 + (months / 12).toInt()  // 1년 3점, 2년 4점, ..., 14년 16점, 15년+ 17점
        }
    }
}
