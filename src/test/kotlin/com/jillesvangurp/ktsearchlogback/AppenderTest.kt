package com.jillesvangurp.ktsearchlogback

import com.jillesvangurp.ktsearch.*
import com.jillesvangurp.searchdsls.querydsl.term
import io.kotest.assertions.timing.eventually
import io.kotest.matchers.longs.shouldBeGreaterThan
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

            val client = SearchClient(KtorRestClient("localhost",9999))
            eventually(20.seconds) {
                // should not throw because the data stream was created
                client.getIndexMappings("applogs")
                // if our mapping is applied, we should be able to query on context.environment
                client.search("applogs") {
                    query=term("context.environment", "tests")
                }.total shouldBeGreaterThan 2
            }

        }
    }
}