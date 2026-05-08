package app.homefit.web.auth

import app.homefit.application.auth.AuthTokens
import app.homefit.domain.user.User
import jakarta.validation.constraints.NotBlank
import java.time.OffsetDateTime

data class GoogleSignInRequest(
    @field:NotBlank
    val idToken: String,
)

/**
 * Sign in with Apple — id_token + (옵션) 사용자 표시 이름.
 * Apple 은 최초 로그인에만 fullName 을 클라이언트에 전달하므로,
 * 클라이언트가 받아서 첫 등록 시 보내준다 (이후엔 null).
 */
data class AppleSignInRequest(
    @field:NotBlank
    val idToken: String,
    val displayName: String? = null,
)

data class RefreshRequest(
    @field:NotBlank
    val refreshToken: String,
)

data class AuthTokensResponse(
    val accessToken: String,
    val refreshToken: String,
    val refreshTokenExpiresAt: OffsetDateTime,
    val user: UserResponse,
) {
    companion object {
        fun from(tokens: AuthTokens): AuthTokensResponse = AuthTokensResponse(
            accessToken = tokens.accessToken,
            refreshToken = tokens.refreshToken,
            refreshTokenExpiresAt = tokens.refreshTokenExpiresAt,
            user = UserResponse.from(tokens.user),
        )
    }
}

data class UserResponse(
    val id: Long,
    val email: String,
    val displayName: String?,
    val profileImageUrl: String?,
) {
    companion object {
        fun from(user: User) = UserResponse(
            id = user.id,
            email = user.email,
            displayName = user.displayName,
            profileImageUrl = user.profileImageUrl,
        )
    }
}
