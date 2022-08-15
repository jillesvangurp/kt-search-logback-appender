package com.jillesvangurp.ktsearchlogback

import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.AppenderBase
import com.jillesvangurp.ktsearch.KtorRestClient
import com.jillesvangurp.ktsearch.SearchClient


@Suppress("MemberVisibilityCanBePrivate") // we need public properties
class KtSearchLogBackAppender : AppenderBase<ILoggingEvent>() {
    // you can override all the public properties via the logback xml config

    var host: String = "localhost"
    var port: Int = 9200
    var userName: String? = null
    var password: String? = null
    var ssl: Boolean = false

    var index = "logs"

    var flushSeconds: Int = 1
    var bulkMaxPageSizw: Int = 200

    private lateinit var logIndexer: LogIndexer

    override fun start() {
        super.start()
        val client = SearchClient(
            KtorRestClient(
                host = host,
                port = port,
                user = userName,
                password = password,
                https = ssl
            )
        )
        logIndexer = LogIndexer(client, index, bulkMaxPageSizw, flushSeconds)

        Runtime.getRuntime().addShutdownHook(Thread {
            // does not seem to ever get called otherwise
            logIndexer.stop()
        })
    }

    override fun append(eventObject: ILoggingEvent?) {
        if (eventObject != null) {
            logIndexer.eventChannel.trySend(eventObject.toLogMessage())
        }
    }

    override fun stop() {
        println("appender stopping")
        logIndexer.stop()
    }
}