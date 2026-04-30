package app.homefit.web.notification

import app.homefit.domain.notification.PushPlatform
import app.homefit.domain.notification.PushTokenRepository
import app.homefit.web.security.CurrentUserId
import jakarta.validation.constraints.NotBlank
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.bind.annotation.ResponseStatus

@RestController
@RequestMapping("/api/v1/push-tokens")
class PushTokenController(
    private val tokens: PushTokenRepository,
) {
    data class RegisterRequest(
        @field:NotBlank val token: String,
        val platform: PushPlatform = PushPlatform.EXPO,
    )

    data class TokenResponse(val token: String, val platform: PushPlatform)

    /** 디바이스 토큰 등록 (로그인 후, 또는 권한 변경 후 재호출). */
    @PostMapping
    fun register(
        @CurrentUserId userId: Long,
        @RequestBody body: RegisterRequest,
    ): TokenResponse {
        val saved = tokens.upsert(userId, body.platform, body.token)
        return TokenResponse(saved.token, saved.platform)
    }

    /** 로그아웃/앱 삭제 시 토큰 제거. body 없이 query 로도 가능하지만 일관성 위해 body 사용. */
    data class DeleteRequest(@field:NotBlank val token: String)

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun unregister(
        @CurrentUserId userId: Long,
        @RequestBody body: DeleteRequest,
    ) {
        tokens.deleteByToken(body.token)
    }
}
