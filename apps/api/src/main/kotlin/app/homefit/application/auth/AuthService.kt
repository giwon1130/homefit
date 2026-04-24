package app.homefit.application.auth

import app.homefit.domain.user.User
import app.homefit.domain.user.UserRepository
import app.homefit.domain.user.UserUpsertInput
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
