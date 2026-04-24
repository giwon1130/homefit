package app.homefit.application.auth

import app.homefit.config.AuthProperties
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class JwtServiceTest {

    private val service = JwtService(
        AuthProperties(
            jwtSecret = "test-secret-32-bytes-minimum-for-hmac-sha-256-validation",
            accessTokenTtlMinutes = 15,
            refreshTokenTtlDays = 30,
            google = AuthProperties.GoogleOAuthProps(clientId = "test"),
        ),
    )

    @Test
    fun `issues and verifies access token`() {
        val token = service.issueAccessToken(42L)
        assertThat(service.verifyAccessToken(token)).isEqualTo(42L)
    }

    @Test
    fun `rejects tampered token`() {
        val token = service.issueAccessToken(1L)
        val tampered = token.dropLast(5) + "aaaaa"
        assertThat(service.verifyAccessToken(tampered)).isNull()
    }

    @Test
    fun `rejects token signed by different secret`() {
        val other = JwtService(
            AuthProperties(
                jwtSecret = "different-secret-32-bytes-minimum-for-hmac-256-ok",
                accessTokenTtlMinutes = 15,
                refreshTokenTtlDays = 30,
                google = AuthProperties.GoogleOAuthProps(clientId = "test"),
            ),
        )
        val foreignToken = other.issueAccessToken(1L)
        assertThat(service.verifyAccessToken(foreignToken)).isNull()
    }
}
