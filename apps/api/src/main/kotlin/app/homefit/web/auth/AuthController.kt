package app.homefit.web.auth

import app.homefit.application.auth.AuthService
import app.homefit.domain.user.UserRepository
import app.homefit.web.security.CurrentUserId
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

@RestController
@RequestMapping("/api/v1/auth")
class AuthController(
    private val authService: AuthService,
    private val users: UserRepository,
) {
    @PostMapping("/google")
    fun google(@Valid @RequestBody req: GoogleSignInRequest): AuthTokensResponse {
        val tokens = authService.signInWithGoogle(req.idToken)
            ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid google id token")
        return AuthTokensResponse.from(tokens)
    }

    @PostMapping("/refresh")
    fun refresh(@Valid @RequestBody req: RefreshRequest): AuthTokensResponse {
        val tokens = authService.refresh(req.refreshToken)
            ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "invalid refresh token")
        return AuthTokensResponse.from(tokens)
    }

    @GetMapping("/me")
    fun me(@CurrentUserId userId: Long): UserResponse {
        val user = users.findById(userId)
            ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED)
        return UserResponse.from(user)
    }
}
