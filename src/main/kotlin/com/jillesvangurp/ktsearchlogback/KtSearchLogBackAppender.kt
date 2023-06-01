package com.jillesvangurp.ktsearchlogback

import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.AppenderBase
import com.jillesvangurp.ktsearch.KtorRestClient
import com.jillesvangurp.ktsearch.SearchClient
import kotlinx.coroutines.*
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds


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

    @Deprecated("renamed", ReplaceWith("manageDataStreamAndTemplates"))
    var createDataStream: Boolean = false

    /** attempt to (re) create templates and data streams. Leave to false if you want to control this manually. */
    var manageDataStreamAndTemplates: Boolean = false

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
    private lateinit var client: SearchClient
    internal var templateInitialized: Boolean = false

    private val coerceLongFields by lazy { coerceMdcFieldsToLong.split(',').map { it.trim() }.toSet() }
    private val coerceDoubleFields by lazy { coerceMdcFieldsToDouble.split(',').map { it.trim() }.toSet() }
    override fun start() {
        println("starting logging-es-calls: $logElasticSearchCalls")
        client = SearchClient(
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

        manageDatastream()

        logIndexer = LogIndexer(this, client, dataStreamName, bulkMaxPageSizw, flushSeconds)
        println("started log indexer")
        // so you can detect application restarts
        Runtime.getRuntime().addShutdownHook(Thread {
            // does not seem to ever get called otherwise
            logIndexer.stop()
        })
        super.start()
    }

    private fun manageDatastream() {
        runBlocking {
            CoroutineScope(CoroutineName("manage templates") + Dispatchers.IO).launch() {
                try {
                    // make sure logging has properly initialized
                    withTimeout(10.seconds) {
                        while (!isStarted) {
                            delay(10.milliseconds)
                        }
                    }
                    if (manageDataStreamAndTemplates) {
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
                            warn(e, "manage data stream failed with exception")
                            e.printStackTrace()
                            false
                        }
                        log("data stream created: $created")

                    }
                } catch (e: Throwable) {
                    e.printStackTrace()
                }
                // LogIndexer.flush waits until this is true; this way no messages are indexed before we have ilm and templates
                templateInitialized = true
            }
        }
    }

    override fun append(eventObject: ILoggingEvent?) {
        if (eventObject != null) {
            logIndexer.eventChannel.trySend(
                eventObject.toLogMessage(
                    contextVariableFilter,
                    coerceLongFields,
                    coerceDoubleFields
                )
            )
        }
    }

    override fun stop() {
        log("appender stopping")
        logIndexer.stop()
    }
}