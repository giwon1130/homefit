package app.homefit.web

import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1")
class HealthController {
    @GetMapping("/ping")
    fun ping(): Map<String, String> = mapOf("status" to "ok", "service" to "homefit-api")
}
