package app.homefit.infrastructure.oauth

import app.homefit.config.AuthProperties
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import org.springframework.stereotype.Component

data class GoogleUserInfo(
    val subject: String,
    val email: String,
    val emailVerified: Boolean,
    val name: String?,
    val pictureUrl: String?,
)

@Component
class GoogleIdTokenVerifierAdapter(props: AuthProperties) {
    private val verifier: GoogleIdTokenVerifier = GoogleIdTokenVerifier.Builder(
        NetHttpTransport(), GsonFactory.getDefaultInstance(),
    )
        .setAudience(listOf(props.google.clientId))
        .build()

    /** 유효한 id_token이면 사용자 정보 반환, 아니면 null. */
    fun verify(idTokenString: String): GoogleUserInfo? {
        val token = verifier.verify(idTokenString) ?: return null
        val payload = token.payload
        val email = payload.email ?: return null
        return GoogleUserInfo(
            subject = payload.subject,
            email = email,
            emailVerified = payload.emailVerified ?: false,
            name = payload["name"] as? String,
            pictureUrl = payload["picture"] as? String,
        )
    }
}
