package com.jillesvangurp.ktsearchlogback

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import org.junit.jupiter.api.Test
import org.slf4j.MDC
import kotlin.time.Duration.Companion.seconds

val logger = KotlinLogging.logger {}

class AppenderTest {
    @Test
    fun shouldLogSomeStuff() {
        runBlocking {
            logger.info { "hello world" }
            MDC.put("test", "value")
            logger.error { "another one" }
            logger.warn { "last one" }
            delay(1.seconds)
        }
    }
}