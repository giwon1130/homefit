package app.homefit.infrastructure.persistence

import app.homefit.domain.listing.admin.ListingAdminRepository
import app.homefit.domain.listing.admin.ListingPatch
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository

@Repository
class JdbcListingAdminRepository(
    private val jdbc: NamedParameterJdbcTemplate,
) : ListingAdminRepository {

    /**
     * COALESCE 로 patch 의 null 필드는 기존값 유지. 좌표 채워지면 geocoded_at도 갱신.
     */
    override fun updateCuratedFields(id: Long, patch: ListingPatch): Int {
        val sql = """
            UPDATE listings SET
                name         = COALESCE(:name, name),
                address      = COALESCE(:address, address),
                sido         = COALESCE(:sido, sido),
                sigungu      = COALESCE(:sigungu, sigungu),
                latitude     = COALESCE(:lat, latitude),
                longitude    = COALESCE(:lng, longitude),
                total_supply = COALESCE(:total, total_supply),
                geocoded_at  = CASE WHEN :lat IS NOT NULL THEN now() ELSE geocoded_at END,
                updated_at   = now()
            WHERE id = :id
        """.trimIndent()
        return jdbc.update(
            sql,
            MapSqlParameterSource()
                .addValue("id", id)
                .addValue("name", patch.name)
                .addValue("address", patch.address)
                .addValue("sido", patch.sido)
                .addValue("sigungu", patch.sigungu)
                .addValue("lat", patch.latitude)
                .addValue("lng", patch.longitude)
                .addValue("total", patch.totalSupply),
        )
    }
}
