package com.jillesvangurp.ktsearchlogback

import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.AppenderBase
import com.jillesvangurp.ktsearch.KtorRestClient
import com.jillesvangurp.ktsearch.SearchClient
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import kotlin.time.Duration.Companion.days

private val logger = KotlinLogging.logger { }

@Suppress("MemberVisibilityCanBePrivate", "unused") // we need public properties
class KtSearchLogBackAppender : AppenderBase<ILoggingEvent>() {
    // you can override all the public properties via the logback xml config

    /** Will log a lot of detail. Useful for debugging when the appender isn't working as expected. */
    var verbose = false
    /** Leave this off unless you have an issue with elasticsearch that you need to diagnose */
    var logElasticSearchCalls = false
    var host: String = "localhost"
    var port: Int = 9200
    var userName: String? = null
    var password: String? = null
    var ssl: Boolean = false

    /** maximum time to wait until flushing messages to Elasticsearch */
    var flushSeconds: Int = 5
    /** maximum bulk request page size before flushing. */
    var bulkMaxPageSizw: Int = 200
    /** attempt to (re) create templates and datas treams. Leave to false if you want to control this manually. */
    var createDataStream: Boolean = false

    /** Elasticsearch only feature, leave disabled for opensearch and set up the os equivalent manually */
    var configureIlm = false

    var dataStreamName = "applogs"

    // ILM settings below

    var hotRollOverGb = 5
    var hotMaxAge = "1d"
    var numberOfReplicas = 1
    var numberOfShards = 1
    var warmMinAgeDays = 3
    var deleteMinAgeDays = 30
    var warmShrinkShards = 1
    var warmSegments = 1
    var contextVariableFilterRe = ""

    /** comma separated list of mdc fields (without mdc prefix), will be coerced to Long in the json */
    var coerceMdcFieldsToLong = ""
    var coerceMdcFieldsToDouble = ""

    private val contextVariableFilter: Regex? by lazy {
        contextVariableFilterRe.takeIf { it.isNotBlank() }?.let {
            try {
                it.toRegex()
            } catch (e: Exception) {
                log("Error parsing $it: ${e.message}")
                null
            }
        }
    }
    private lateinit var logIndexer: LogIndexer

    private val coerceLongFields by lazy { coerceMdcFieldsToLong.split(',').map { it.trim() }.toSet()}
    private val coerceDoubleFields by lazy { coerceMdcFieldsToDouble.split(',').map { it.trim() }.toSet()}
    override fun start() {
        log("starting")
        super.start()
        val client = SearchClient(
            KtorRestClient(
                host = host,
                port = port,
                user = userName,
                password = password,
                https = ssl,
                logging = logElasticSearchCalls
            )
        )
        log("connecting to $host:$port using ssl $ssl with user: $userName and password: ${password?.map { 'x' }}")
        if (createDataStream) {
            runBlocking {
                val created = try {
                    client.manageDataStream(
                        prefix = dataStreamName,
                        hotRollOverGb = hotRollOverGb,
                        hotMaxAge = hotMaxAge,
                        numberOfReplicas = numberOfReplicas,
                        numberOfShards = numberOfShards,
                        warmMinAge = warmMinAgeDays.days,
                        deleteMinAge = deleteMinAgeDays.days,
                        warmShrinkShards = warmShrinkShards,
                        warmSegments = warmSegments,
                        configureIlm = configureIlm,
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                    false
                }
                log("data stream created: $created")
            }
        }

        logIndexer = LogIndexer(client, dataStreamName, bulkMaxPageSizw, flushSeconds)
        log("started log indexer")
        // so you can detect application restarts
        logger.info { "log appender init" }
        Runtime.getRuntime().addShutdownHook(Thread {
            // does not seem to ever get called otherwise
            logIndexer.stop()
        })
    }

    private fun log(message: String) {
        if (verbose) {
            logger.info { message }
            println("kt-search_logback-appender: $message")
        }
    }

    override fun append(eventObject: ILoggingEvent?) {
        if (eventObject != null) {
            logIndexer.eventChannel.trySend(eventObject.toLogMessage(contextVariableFilter, coerceLongFields, coerceDoubleFields))
        }
    }

    override fun stop() {
        log("appender stopping")
        logIndexer.stop()
    }
}