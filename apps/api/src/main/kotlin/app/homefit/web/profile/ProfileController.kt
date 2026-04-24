package app.homefit.web.profile

import app.homefit.application.profile.ProfileService
import app.homefit.application.profile.ScoreService
import app.homefit.web.security.CurrentUserId
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/profile")
class ProfileController(
    private val profileService: ProfileService,
    private val scoreService: ScoreService,
) {
    @GetMapping
    fun get(@CurrentUserId userId: Long): FullProfileResponse =
        FullProfileResponse.from(profileService.getFullProfile(userId))

    @PutMapping
    fun putCore(@CurrentUserId userId: Long, @Valid @RequestBody body: ProfileCoreDto) {
        profileService.saveCore(userId, body.toDomain())
    }

    @PutMapping("/household-members")
    fun putMembers(@CurrentUserId userId: Long, @Valid @RequestBody body: List<HouseholdMemberDto>) {
        profileService.replaceHouseholdMembers(userId, body.map { it.toDomain() })
    }

    @PutMapping("/incomes")
    fun putIncomes(@CurrentUserId userId: Long, @Valid @RequestBody body: List<IncomeDto>) {
        profileService.replaceIncomes(userId, body.map { it.toDomain() })
    }

    @PutMapping("/assets")
    fun putAssets(@CurrentUserId userId: Long, @Valid @RequestBody body: AssetsDto) {
        profileService.saveAssets(userId, body.toDomain())
    }

    @PutMapping("/residences")
    fun putResidences(@CurrentUserId userId: Long, @Valid @RequestBody body: List<ResidenceDto>) {
        profileService.replaceResidences(userId, body.map { it.toDomain() })
    }

    @PutMapping("/workplaces")
    fun putWorkplaces(@CurrentUserId userId: Long, @Valid @RequestBody body: List<WorkplaceDto>) {
        profileService.replaceWorkplaces(userId, body.map { it.toDomain() })
    }

    @PutMapping("/preferences")
    fun putPreferences(@CurrentUserId userId: Long, @Valid @RequestBody body: PreferencesDto) {
        profileService.savePreferences(userId, body.toDomain())
    }

    @PutMapping("/housing-history")
    fun putHistory(@CurrentUserId userId: Long, @Valid @RequestBody body: List<HousingHistoryDto>) {
        profileService.replaceHousingHistory(userId, body.map { it.toDomain() })
    }

    @GetMapping("/score")
    fun getScore(@CurrentUserId userId: Long): ScoreResponse =
        ScoreResponse.from(scoreService.computeScore(userId))
}
