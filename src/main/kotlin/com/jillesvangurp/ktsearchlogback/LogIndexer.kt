package com.jillesvangurp.ktsearchlogback

import com.jillesvangurp.ktsearch.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.time.Duration.Companion.seconds

class LogIndexer(
    private val client: SearchClient,
    private val index: String,
    private val bulkMaxPageSize: Int,
    private val flushSeconds: Int,
) {
    private var session: BulkSession
    internal val eventChannel = Channel<LogMessage>(
        capacity = 1000,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    private val flushJob: Job
    private val indexJob: Job

    private var lastIndexed: Instant? = null
    private var running = true

    private val loggingScope = CoroutineScope(Dispatchers.Default + CoroutineName("kt-search"))

    private var successCount=0
    private var failCount=0
    private var errorCount=0
    private var receiveCount=0

    init {
        try {
            session = runBlocking {
                 client.bulkSession(
                    bulkSize = bulkMaxPageSize,
                    closeOnRequestError = false,
                    failOnFirstError = false,
                    // do some bookkeeping so you can know when you are losing messages
                    callBack = object : BulkItemCallBack {
                        override fun bulkRequestFailed(e: Exception, ops: List<Pair<String, String?>>) {
                            println("bulkRequest failed ${e.message}")
                            e.printStackTrace()
                            errorCount++
                        }

                        override fun itemFailed(operationType: OperationType, item: BulkResponse.ItemDetails) {
                            println("bulkItem failed ${operationType.name}")
                            failCount++
                        }

                        override fun itemOk(operationType: OperationType, item: BulkResponse.ItemDetails) {
                            successCount++
                        }

                    }
                )
            }
            flushJob = loggingScope.launch {
                try {
                    while (running) {
                        val now = Clock.System.now()
                        val check = lastIndexed
                        if (check != null) {
                            if (now.minus(check).inWholeSeconds > flushSeconds) {
                                try {
                                    session.flush()
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                    println("Error flushing: ${e.message}")
                                }
                                lastIndexed = now
                            }
                        } else {
                            lastIndexed = now
                        }
                        delay(1.seconds)
                    }
                } catch (e: Exception) {
                    println("Logback appender flush loop exiting abnormally ${e.message}")
                    e.printStackTrace()
                }
            }
            indexJob = loggingScope.launch {
                try {
                    while (running) {
                        try {
                            val e = eventChannel.receive()
                            receiveCount++
                            session.create(index=index,doc = e)
                        } catch (e: Exception) {
                            println("indexing error: ${e.message}")
                            e.printStackTrace()
                        }
                    }
                } catch (e: Exception) {
                    println("Logback appender session create loop exiting abnormally ${e.message}")
                    e.printStackTrace()
                }
            }
            println("Started successfully")
        } catch (e: Exception) {
            println("Error initializing kt-search log appender")
            e.printStackTrace()
            throw e
        }
    }

    fun stop() {
        println("stopping")
        runBlocking {
            session.flush()
            session.close()
            running = false
            flushJob.cancel()
        }
        indexJob.cancel()
        println("closed es logback appender: received: $receiveCount, indexed:$successCount, failed: $failCount, errors: $errorCount")
    }
}