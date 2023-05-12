package com.jillesvangurp.ktsearchlogback

import com.jillesvangurp.ktsearch.*
import com.jillesvangurp.searchdsls.querydsl.term
import io.kotest.assertions.timing.eventually
import io.kotest.matchers.collections.contain
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.longs.shouldBeGreaterThan
import io.kotest.matchers.shouldNot
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
            try {
                error("oopsie")
            } catch (e: Exception) {
                logger.error(e) { "stacktrace" }
            }
            logger.warn { "last one" }
            delay(1.seconds)

            val client = SearchClient(KtorRestClient("localhost",9999))
            eventually(20.seconds) {
                // should not throw because the data stream was created
                client.getIndexMappings("applogs")
                // if our mapping is applied, we should be able to query on context.environment
                val resp = client.search("applogs") {
                    resultSize=100
                    query = term("context.environment", "tests")
                }
                resp.total shouldBeGreaterThan 2
                val hits = resp.parseHits<LogMessage>(DEFAULT_JSON)
                hits.first().let { m ->
                    (m.context?.keys ?: setOf()) shouldContain "host"
                    (m.context?.keys ?: setOf()) shouldNot contain("exclude")
                }
                hits.first { it.message == "stacktrace"}.let {
                    (it.exceptionList?.first()?.stackTrace?.size?:-1) shouldBeGreaterThan 1
                }
            }

        }
    }
}