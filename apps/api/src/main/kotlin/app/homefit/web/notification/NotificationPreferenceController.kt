package app.homefit.web.notification

import app.homefit.domain.user.UserRepository
import app.homefit.web.security.CurrentUserId
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 알림 환경설정 GET/PUT. 현재 D-1 이메일 토글 한 가지.
 * 향후 푸시(D-1, 결과발표 등) 채널이 추가되면 동일 endpoint 에 필드만 늘림.
 */
@RestController
@RequestMapping("/api/v1/notifications/preferences")
class NotificationPreferenceController(
    private val users: UserRepository,
) {
    data class PreferenceResponse(val emailEnabled: Boolean, val pushEnabled: Boolean)

    /** PUT 시 둘 중 한쪽만 보내도 되도록 nullable. null 이면 변경 안 함. */
    data class PreferenceUpdate(
        val emailEnabled: Boolean? = null,
        val pushEnabled: Boolean? = null,
    )

    @GetMapping
    fun get(@CurrentUserId userId: Long): PreferenceResponse {
        return PreferenceResponse(
            emailEnabled = users.isEmailNotificationsEnabled(userId),
            pushEnabled = users.isPushNotificationsEnabled(userId),
        )
    }

    @PutMapping
    fun update(
        @CurrentUserId userId: Long,
        @RequestBody body: PreferenceUpdate,
    ): PreferenceResponse {
        body.emailEnabled?.let { users.setEmailNotificationsEnabled(userId, it) }
        body.pushEnabled?.let { users.setPushNotificationsEnabled(userId, it) }
        return PreferenceResponse(
            emailEnabled = users.isEmailNotificationsEnabled(userId),
            pushEnabled = users.isPushNotificationsEnabled(userId),
        )
    }
}
