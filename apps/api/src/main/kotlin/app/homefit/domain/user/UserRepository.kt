package app.homefit.domain.user

interface UserRepository {
    fun upsert(input: UserUpsertInput): User
    fun findById(id: Long): User?
}
