package com.jillesvangurp.ktsearchlogback

import com.jillesvangurp.ktsearch.*
import com.jillesvangurp.searchdsls.querydsl.matchAll
import com.jillesvangurp.searchdsls.querydsl.range
import com.jillesvangurp.searchdsls.querydsl.term
import io.kotest.assertions.timing.eventually
import io.kotest.matchers.collections.contain
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.longs.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNot
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import org.junit.jupiter.api.RepeatedTest
import org.slf4j.MDC
import kotlin.time.Duration.Companion.seconds

val logger = KotlinLogging.logger {}

class AppenderTest {
    @RepeatedTest(2) // run it twice so we test restart and fresh start
    fun shouldLogSomeStuff() {
        runBlocking {
            val client = SearchClient(KtorRestClient("localhost",9999))
            runCatching {
                client.deleteByQuery("applogs") {
                    query = matchAll()
                }
            }

            logger.info { "hello world" }
            MDC.put("test", "value")
            MDC.put("duration_ms", "42")
            MDC.put("amount_processed", "2")
            MDC.put("improbability_level", "0.01")

            logger.error { "another one" }
            try {
                error("oopsie")
            } catch (e: Exception) {
                logger.error(e) { "stacktrace" }
            }
            MDC.put("duration_ms", "420")
            MDC.put("amount_processed", "5")
            MDC.put("improbability_level", "0.9")
            logger.warn { "last one" }
            MDC.clear()
            delay(1.seconds)

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
            client.search("applogs") {
                query = range("mdc.duration_ms") {
                    gt = 100
                }
            }.also {
                println(it.hits)
            }.total shouldBe 1
            client.search("applogs") {
                query = range("mdc.amount_processed") {
                    gt = 3
                    lt = 6
                }
            }.total shouldBe 1
            client.search("applogs") {
                query = range("mdc.improbability_level") {
                    gt = 0.3
                }
            }.total shouldBe 1

        }
    }
}