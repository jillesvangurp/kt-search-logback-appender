package com.jillesvangurp.ktsearchlogback

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.time.delay
import mu.KotlinLogging
import org.junit.jupiter.api.Test
import org.slf4j.MDC
import java.time.Duration

val logger = KotlinLogging.logger {}

class AppenderTest {
    @Test
    fun shouldLogSomeStuff() {
        logger.info { "hello world" }
        MDC.put("test","value")
        logger.error { "another one" }
        runBlocking {
            delay(Duration.ofSeconds(5))
        }
    }
}