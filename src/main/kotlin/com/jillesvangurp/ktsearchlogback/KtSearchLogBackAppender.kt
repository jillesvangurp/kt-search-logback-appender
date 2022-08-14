package com.jillesvangurp.ktsearchlogback

import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.AppenderBase
import com.jillesvangurp.ktsearch.BulkSession
import com.jillesvangurp.ktsearch.KtorRestClient
import com.jillesvangurp.ktsearch.SearchClient
import com.jillesvangurp.ktsearch.bulkSession
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.time.Duration.Companion.seconds


class KtSearchLogBackAppender : AppenderBase<ILoggingEvent>() {

    var host: String = "localhost"
    var port: Int = 9200
    var userName: String? = null
    var password: String? = null
    var ssl: Boolean = false

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

    private lateinit var session: BulkSession
    private val eventChannel= Channel<ILoggingEvent>(capacity = 1000, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private lateinit var indexJob: Job

    private var lastIndexed: Instant?=null

    override fun start() {
        super.start()
        CoroutineScope(Dispatchers.Default).launch {
            while (true) {
                println("checking")
                val now = Clock.System.now()
                val check = lastIndexed
                if(check != null) {
                    if(now.minus(check).inWholeSeconds > 1) {
                        println("flushing")
                        session.flush()
                        lastIndexed=now
                        println("flushed")
                    }
                } else {
                    lastIndexed = now
                }
                delay(1.seconds)
            }
        }
        indexJob = CoroutineScope(Dispatchers.Default).launch {
            session = client.bulkSession(200, target = "logs")

            while(true) {
                val e = eventChannel.receive()
                println("receive!")
                try {
                    session.index("""{"message": "${e.message}"}""".trimMargin())
                    println("indexing")
                } catch (e: Exception) {
                    println("Oopsie")
                }
            }
        }
    }

    override fun append(eventObject: ILoggingEvent?) {
        println("append")
        if(eventObject != null) {
            eventChannel.trySend(eventObject)
        }
    }

    override fun stop() {
        println("Stopping")
        runBlocking {
            try {
                eventChannel.close()
            } catch (e: Exception) {
                println("Closing event channel failed")
            }
            session.flush()
            session.close()
        }
        indexJob.cancel()
        println("closed es logback appender normally")
    }
}