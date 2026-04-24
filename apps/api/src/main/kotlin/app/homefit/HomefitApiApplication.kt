package app.homefit

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class HomefitApiApplication

fun main(args: Array<String>) {
    runApplication<HomefitApiApplication>(*args)
}
