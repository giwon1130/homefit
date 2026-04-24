package app.homefit.application.profile

import app.homefit.domain.profile.ProfileRepository
import app.homefit.domain.profile.ScoreBreakdown
import app.homefit.domain.profile.ScoreCalculator
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ScoreService(
    private val repo: ProfileRepository,
) {
    @Transactional(readOnly = true)
    fun computeScore(userId: Long): ScoreBreakdown {
        val core = repo.findCore(userId) ?: return ScoreBreakdown(0, 0, 0, 0, listOf("프로필 미입력"))
        val members = repo.findHouseholdMembers(userId)
        return ScoreCalculator.calculate(core, members)
    }
}
