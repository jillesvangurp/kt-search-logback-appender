package com.jillesvangurp.ktsearchlogback

import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.AppenderBase
import com.jillesvangurp.ktsearch.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlin.time.Duration.Companion.seconds

@Serializable
data class LogMessage(
    val message: String,
    val logger: String,
    val thread: String,
    val level: String,
    val timestamp: Instant = Clock.System.now(),
    val mdc: Map<String,String>? = null,
    val context: Map<String,String>? = null,
)

fun ILoggingEvent.toLogMessage() = LogMessage(
    message = message,
    logger = loggerName,
    thread = threadName,
    level = level.levelStr,
    mdc = mdcPropertyMap.takeIf { (it?.size ?: 0) > 0 },
    context = if(loggerContextVO != null) this.loggerContextVO.propertyMap.takeIf { (it?.size ?: 0) > 0 } else null,
)

class KtSearchLogBackAppender : AppenderBase<ILoggingEvent>() {
    // you can override all the public properties via the logback xml config

    var host: String = "localhost"
    var port: Int = 9200
    var userName: String? = null
    var password: String? = null
    var ssl: Boolean = false

    var index="logs"

    var flushSeconds: Int = 1
    var bulkMaxPageSizw: Int = 200


    // lazy create this with whatever was configured
    val client by lazy {
        println("$host:$port")
        SearchClient(KtorRestClient(
            host = host,
            port = port,
            user = userName,
            password = password,
            https = ssl
        ))
    }

    // some global state
    private lateinit var session: BulkSession
    private val eventChannel= Channel<LogMessage>(capacity = 1000, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private lateinit var flushJob: Job
    private lateinit var indexJob: Job

    private var lastIndexed: Instant?=null
    private var running=true

    override fun start() {
        super.start()
        runBlocking {
            session = client.bulkSession(
                bulkSize = bulkMaxPageSizw,
                target = index,
                closeOnRequestError = false,
                failOnFirstError = false
            )
        }
        flushJob = CoroutineScope(Dispatchers.Default).launch {
            while (running) {
                val now = Clock.System.now()
                val check = lastIndexed
                if(check != null) {
                    if(now.minus(check).inWholeSeconds > flushSeconds) {
                        try {
                            session.flush()
                        } catch (e: Exception) {
                            println("Error flushing: ${e.message}")
                        }
                        lastIndexed=now
                    }
                } else {
                    lastIndexed = now
                }
                delay(1.seconds)
            }
        }
        indexJob = CoroutineScope(Dispatchers.Default).launch {
            while(running) {
                val e = eventChannel.receive()
                try {
                    println(e)
                    session.index(doc = e)
                } catch (e: Exception) {
                    println("indexing error: ${e.message}")
                }
            }
        }
    }

    override fun append(eventObject: ILoggingEvent?) {
        if(eventObject != null) {
            eventChannel.trySend(eventObject.toLogMessage())
        }
    }

    override fun stop() {
        // FIXME never seems to be called during the tests
        println("Stopping")
        runBlocking {
            try {
                eventChannel.close()
            } catch (e: Exception) {
                println("Closing event channel failed: ${e.message}")
            }
            running=false
            flushJob.cancel()
            session.flush()
            session.close()
        }
        indexJob.cancel()
        println("closed es logback appender normally")
    }
}