package app.homefit.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "homefit.auth")
data class AuthProperties(
    val jwtSecret: String,
    val accessTokenTtlMinutes: Long = 15,
    val refreshTokenTtlDays: Long = 30,
    val google: GoogleOAuthProps,
) {
    data class GoogleOAuthProps(
        val clientId: String,
    )
}
