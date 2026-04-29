package app.homefit.domain.user

interface UserRepository {
    fun upsert(input: UserUpsertInput): User
    fun findById(id: Long): User?

    /** D-1 이메일 알림 수신 여부. */
    fun isEmailNotificationsEnabled(userId: Long): Boolean
    fun setEmailNotificationsEnabled(userId: Long, enabled: Boolean)
}
