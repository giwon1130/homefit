package app.homefit.ingestion

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
class HomefitIngestionApplication

fun main(args: Array<String>) {
    runApplication<HomefitIngestionApplication>(*args)
}
