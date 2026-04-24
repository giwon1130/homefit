package app.homefit.domain.user

import java.time.OffsetDateTime

data class User(
    val id: Long,
    val email: String,
    val oauthProvider: String,
    val oauthSubject: String,
    val displayName: String?,
    val profileImageUrl: String?,
    val createdAt: OffsetDateTime,
    val lastLoginAt: OffsetDateTime?,
)

data class UserUpsertInput(
    val email: String,
    val oauthProvider: String,
    val oauthSubject: String,
    val displayName: String?,
    val profileImageUrl: String?,
)
