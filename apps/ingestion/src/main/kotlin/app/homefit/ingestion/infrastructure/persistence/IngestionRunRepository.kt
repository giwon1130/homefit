package app.homefit.ingestion.infrastructure.persistence

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.jdbc.support.GeneratedKeyHolder
import org.springframework.stereotype.Repository

@Repository
class IngestionRunRepository(
    private val jdbc: NamedParameterJdbcTemplate,
) {
    fun start(source: String): Long {
        val sql = """
            INSERT INTO ingestion_runs (source, status)
            VALUES (:source, 'RUNNING') RETURNING id
        """.trimIndent()
        val keys = GeneratedKeyHolder()
        jdbc.update(sql, MapSqlParameterSource("source", source), keys, arrayOf("id"))
        return keys.key?.toLong() ?: error("failed to start ingestion run")
    }

    fun succeed(id: Long, pagesRead: Int, rowsUpserted: Int) {
        jdbc.update(
            """
            UPDATE ingestion_runs
               SET status = 'SUCCESS', finished_at = now(),
                   pages_read = :pages, rows_upserted = :rows
             WHERE id = :id
            """.trimIndent(),
            MapSqlParameterSource()
                .addValue("id", id)
                .addValue("pages", pagesRead)
                .addValue("rows", rowsUpserted),
        )
    }

    fun fail(id: Long, error: String) {
        jdbc.update(
            """
            UPDATE ingestion_runs
               SET status = 'FAILED', finished_at = now(), error = :error
             WHERE id = :id
            """.trimIndent(),
            MapSqlParameterSource()
                .addValue("id", id)
                .addValue("error", error.take(2000)),
        )
    }
}
