package app.homefit.infrastructure.persistence

import app.homefit.domain.favorite.FavoriteRepository
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository

@Repository
class JdbcFavoriteRepository(
    private val jdbc: NamedParameterJdbcTemplate,
) : FavoriteRepository {
    override fun add(userId: Long, listingId: Long) {
        jdbc.update(
            """
            INSERT INTO favorites (user_id, listing_id)
            VALUES (:uid, :lid)
            ON CONFLICT (user_id, listing_id) DO NOTHING
            """.trimIndent(),
            MapSqlParameterSource().addValue("uid", userId).addValue("lid", listingId),
        )
    }

    override fun remove(userId: Long, listingId: Long) {
        jdbc.update(
            "DELETE FROM favorites WHERE user_id = :uid AND listing_id = :lid",
            MapSqlParameterSource().addValue("uid", userId).addValue("lid", listingId),
        )
    }

    override fun listIds(userId: Long): List<Long> = jdbc.query(
        "SELECT listing_id FROM favorites WHERE user_id = :uid ORDER BY created_at DESC",
        MapSqlParameterSource("uid", userId),
    ) { rs, _ -> rs.getLong(1) }

    override fun exists(userId: Long, listingId: Long): Boolean = jdbc.queryForObject(
        "SELECT EXISTS(SELECT 1 FROM favorites WHERE user_id = :uid AND listing_id = :lid)",
        MapSqlParameterSource().addValue("uid", userId).addValue("lid", listingId),
        Boolean::class.java,
    ) ?: false
}
