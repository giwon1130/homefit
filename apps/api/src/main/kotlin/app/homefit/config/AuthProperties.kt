package app.homefit.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "homefit.auth")
data class AuthProperties(
    val jwtSecret: String,
    val accessTokenTtlMinutes: Long = 15,
    val refreshTokenTtlDays: Long = 30,
    val google: GoogleOAuthProps,
    val apple: AppleOAuthProps = AppleOAuthProps(),
) {
    data class GoogleOAuthProps(
        val clientId: String,
    )

    /**
     * Sign in with Apple — id_token 검증용 audience.
     * - audiences 에는 iOS 앱 bundle id ("app.homefit.mobile") 와 (필요시) 웹 Service ID 함께 등록.
     *   Apple JWT 의 aud claim 이 이 중 하나와 일치해야 한다.
     * - 비어있으면 Apple 로그인 자체를 비활성화 (503 응답).
     */
    data class AppleOAuthProps(
        val audiences: List<String> = emptyList(),
    )
}
