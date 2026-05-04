package app.homefit.application.profile

import app.homefit.domain.profile.ProfileRepository
import app.homefit.domain.profile.ScoreBreakdown
import app.homefit.domain.profile.ScoreCalculator
import app.homefit.domain.profile.ScoreItem
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ScoreService(
    private val repo: ProfileRepository,
) {
    @Transactional(readOnly = true)
    fun computeScore(userId: Long): ScoreBreakdown {
        val core = repo.findCore(userId) ?: return EMPTY
        val members = repo.findHouseholdMembers(userId)
        return ScoreCalculator.calculate(core, members)
    }

    companion object {
        private val EMPTY = ScoreBreakdown(
            noHomePeriod = ScoreItem(0, ScoreBreakdown.MAX_NO_HOME, "프로필 미입력"),
            dependents = ScoreItem(0, ScoreBreakdown.MAX_DEPENDENTS, "프로필 미입력"),
            accountAge = ScoreItem(0, ScoreBreakdown.MAX_ACCOUNT, "프로필 미입력"),
            notes = listOf("프로필을 입력하면 가점이 계산됩니다."),
        )
    }
}
