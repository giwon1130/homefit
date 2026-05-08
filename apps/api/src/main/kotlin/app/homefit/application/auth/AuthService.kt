package app.homefit.application.auth

import app.homefit.domain.user.User
import app.homefit.domain.user.UserRepository
import app.homefit.domain.user.UserUpsertInput
import app.homefit.infrastructure.oauth.AppleIdTokenVerifier
import app.homefit.infrastructure.oauth.GoogleIdTokenVerifierAdapter
import org.springframework.stereotype.Service
import java.time.OffsetDateTime

data class AuthTokens(
    val accessToken: String,
    val refreshToken: String,
    val refreshTokenExpiresAt: OffsetDateTime,
    val user: User,
)

@Service
class AuthService(
    private val googleVerifier: GoogleIdTokenVerifierAdapter,
    private val appleVerifier: AppleIdTokenVerifier,
    private val users: UserRepository,
    private val jwt: JwtService,
    private val refreshTokens: RefreshTokenService,
) {
    /**
     * Google id_token 을 받아 사용자 upsert 후 access/refresh 발급.
     * email_verified=false 이면 거부.
     */
    fun signInWithGoogle(idToken: String): AuthTokens? {
        val info = googleVerifier.verify(idToken) ?: return null
        if (!info.emailVerified) return null
        val user = users.upsert(
            UserUpsertInput(
                email = info.email,
                oauthProvider = "google",
                oauthSubject = info.subject,
                displayName = info.name,
                profileImageUrl = info.pictureUrl,
            ),
        )
        return issueFor(user)
    }

    /**
     * Sign in with Apple — id_token + (선택) 첫 로그인 시 displayName.
     * Apple 은 첫 로그인에만 email/name 을 제공하므로, displayName 은 클라이언트가 전달.
     * email 이 없을 수 있으나 (재로그인) sub 로 기존 사용자 매칭.
     *
     * @param appleEmail 첫 로그인 시 토큰에 들어있는 email — 이후엔 fallback ${sub}@privaterelay.appleid.com
     */
    fun signInWithApple(idToken: String, displayName: String?): AuthTokens? {
        if (!appleVerifier.isEnabled) return null
        val info = appleVerifier.verify(idToken) ?: return null
        // email_verified=false 거나 누락이고 동시에 email 자체가 없으면 — 사용자 식별이 안 됨.
        // Apple 의 private relay (xxx@privaterelay.appleid.com) 는 항상 verified.
        val email = info.email
            ?: "${info.subject}@privaterelay.appleid.com"   // 재로그인 시 토큰에 email 부재 → fallback

        val user = users.upsert(
            UserUpsertInput(
                email = email,
                oauthProvider = "apple",
                oauthSubject = info.subject,
                displayName = displayName?.takeIf { it.isNotBlank() },
                profileImageUrl = null,
            ),
        )
        return issueFor(user)
    }

    fun refresh(refreshToken: String): AuthTokens? {
        val rotated = refreshTokens.rotate(refreshToken) ?: return null
        val user = users.findById(rotated.userId) ?: return null
        val access = jwt.issueAccessToken(user.id)
        return AuthTokens(access, rotated.issued.plaintext, rotated.issued.expiresAt, user)
    }

    private fun issueFor(user: User): AuthTokens {
        val access = jwt.issueAccessToken(user.id)
        val refresh = refreshTokens.issue(user.id)
        return AuthTokens(access, refresh.plaintext, refresh.expiresAt, user)
    }
}
