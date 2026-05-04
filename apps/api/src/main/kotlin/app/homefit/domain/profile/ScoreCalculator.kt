package app.homefit.domain.profile

import java.time.LocalDate
import java.time.Period

/**
 * 민영주택 청약 가점 계산 (국토교통부 고시, 84점 만점).
 *
 * 무주택기간 (32점) + 부양가족 수 (35점) + 청약통장 가입기간 (17점)
 *
 * 정밀 룰 (v2):
 *  - 무주택기간: 만 30세 되는 날 vs 혼인일 중 늦은 날부터 기산 (`no_home_since` 우선).
 *      1년 미만 2점, 1년 단위로 2점씩 가산, 15년 이상 32점.
 *  - 부양가족: 본인 제외. 배우자 + 만 30세 미만 미혼 자녀 + 만 65세 이상 직계존속(부모/조부모).
 *      0명 5점, 1명 10점 ... 6명 이상 35점.
 *  - 청약통장: 6개월 미만 1점, 6~12개월 2점, 1년~2년 3점 ... 14년~15년 16점, 15년 이상 17점.
 *
 * v2 변경점:
 *  - 부양가족 단순 카운트 → 관계/나이 검증 (자녀 30 미만, 부모/조부모 65+).
 *  - 항목별 reason 노트 노출 (UI 가 "왜 N점인지" 표시).
 *  - 항목별 산식/기산일을 노트에 명시.
 *
 * 본 가점은 **민영주택 가점제** 기준이며, 공공/특별공급은 별도 룰이 적용됨을 호출자 UI 에서 안내.
 */
data class ScoreItem(
    val points: Int,
    val max: Int,
    /** 사용자에게 보여줄 1-2 줄 요약. */
    val reason: String,
)

data class ScoreBreakdown(
    val noHomePeriod: ScoreItem,
    val dependents: ScoreItem,
    val accountAge: ScoreItem,
    /** 전역 안내 노트 (예: "민영주택 가점제 기준"). */
    val notes: List<String> = emptyList(),
) {
    val total: Int = noHomePeriod.points + dependents.points + accountAge.points

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
        val noHome = noHomePeriod(core, today)
        val deps = dependents(householdMembers, today)
        val acct = accountAge(core.subscriptionAccountOpenedAt, today)

        val notes = mutableListOf(
            "민영주택 가점제 기준 (84점 만점). 공공·특별공급은 별도 룰이 적용됩니다.",
        )
        if (core.birthDate == null && core.noHomeSince == null) {
            notes.add("생년월일/무주택 기산일을 입력하면 무주택기간 가점이 계산됩니다.")
        }

        return ScoreBreakdown(noHome, deps, acct, notes)
    }

    // ---- 무주택기간 (32점) ----

    /** 기산일: no_home_since 우선, 없으면 max(혼인일, 만 30세 되는 날). */
    internal fun noHomeStart(core: ProfileCore): LocalDate? {
        core.noHomeSince?.let { return it }
        val candidates = listOfNotNull(
            core.marriageDate,
            core.birthDate?.plusYears(30),
        )
        return candidates.maxOrNull()
    }

    private fun noHomePeriod(core: ProfileCore, today: LocalDate): ScoreItem {
        val start = noHomeStart(core)
            ?: return ScoreItem(
                0, ScoreBreakdown.MAX_NO_HOME,
                "기산일 정보 부족 — 생년월일·혼인일·무주택 기산일 중 하나를 입력하세요",
            )

        if (start.isAfter(today)) {
            return ScoreItem(
                0, ScoreBreakdown.MAX_NO_HOME,
                "아직 기산 시점($start) 이전 — 만 30세·혼인일 중 빠른 쪽 도래 후 가점 시작",
            )
        }
        val period = Period.between(start, today)
        val years = period.years
        val pts = when {
            years < 1 -> 2
            years >= 15 -> 32
            else -> 2 + years * 2
        }
        val source = when {
            core.noHomeSince != null -> "무주택 기산일 $start"
            core.marriageDate == start -> "혼인일 $start (만 30세보다 늦음)"
            else -> "만 30세 도달일 $start"
        }
        val basis = if (years >= 15) "15년 이상" else "${years}년 ${period.months}개월"
        return ScoreItem(
            pts, ScoreBreakdown.MAX_NO_HOME,
            "$source 기준 무주택 $basis → $pts 점. (배우자도 무주택이어야 인정 — 별도 검증 필요)",
        )
    }

    // ---- 부양가족 수 (35점) ----

    /** 본인 제외 부양가족 카운트. 관계별 자격 룰 적용. */
    internal fun countDependents(members: List<HouseholdMember>, today: LocalDate): Pair<Int, List<String>> {
        var count = 0
        val warnings = mutableListOf<String>()
        var spouseSeen = false
        var childAgeOver = 0
        var parentAgeUnder = 0

        for (m in members) {
            when (m.relation) {
                HouseholdRelation.SPOUSE -> {
                    if (spouseSeen) continue
                    spouseSeen = true
                    count++
                }
                HouseholdRelation.CHILD -> {
                    val age = ageOrNull(m.birthDate, today)
                    if (age == null || age < 30) {
                        // 생년월일 미입력 시 30세 미만 가정 (보수적 — 사용자 기대치 충족)
                        count++
                    } else {
                        childAgeOver++
                    }
                }
                HouseholdRelation.PARENT, HouseholdRelation.GRANDPARENT -> {
                    val age = ageOrNull(m.birthDate, today)
                    if (age == null || age >= 65) {
                        count++
                    } else {
                        parentAgeUnder++
                    }
                }
                HouseholdRelation.OTHER -> {
                    // 형제/조카 등은 부양가족 미인정
                }
            }
        }
        if (childAgeOver > 0) {
            warnings.add("자녀 ${childAgeOver}명은 만 30세 이상이라 1년+ 동일세대 별도 검증 필요 — 본 계산에서 제외")
        }
        if (parentAgeUnder > 0) {
            warnings.add("직계존속 ${parentAgeUnder}명은 만 65세 미만이라 부양가족 인정 안 됨")
        }
        return count to warnings
    }

    private fun dependents(members: List<HouseholdMember>, today: LocalDate): ScoreItem {
        val (count, warnings) = countDependents(members, today)
        val clamped = count.coerceIn(0, 6)
        val pts = 5 + clamped * 5
        val cap = if (count > 6) " (6명 캡 적용)" else ""
        val warnSuffix = if (warnings.isNotEmpty()) " · ${warnings.joinToString(" · ")}" else ""
        return ScoreItem(
            pts, ScoreBreakdown.MAX_DEPENDENTS,
            "본인 제외 부양가족 ${count}명$cap → $pts 점$warnSuffix",
        )
    }

    private fun ageOrNull(birth: LocalDate?, today: LocalDate): Int? =
        birth?.let { Period.between(it, today).years }

    // ---- 청약통장 가입기간 (17점) ----

    private fun accountAge(openedAt: LocalDate?, today: LocalDate): ScoreItem {
        if (openedAt == null) {
            return ScoreItem(0, ScoreBreakdown.MAX_ACCOUNT, "청약통장 가입일 미입력")
        }
        val months = Period.between(openedAt, today).toTotalMonths()
        val pts = when {
            months < 6 -> 1
            months < 12 -> 2
            months >= 15L * 12 -> 17
            else -> 2 + (months / 12).toInt()
        }
        val basis = when {
            months < 6 -> "6개월 미만"
            months >= 15L * 12 -> "15년 이상"
            else -> "${months / 12}년 ${months % 12}개월"
        }
        return ScoreItem(pts, ScoreBreakdown.MAX_ACCOUNT, "청약통장 가입 $basis → $pts 점")
    }
}
