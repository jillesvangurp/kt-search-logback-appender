package com.jillesvangurp.ktsearchlogback

import ch.qos.logback.classic.spi.ILoggingEvent
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class LogMessage(
    val message: String,
    val logger: String,
    val thread: String,
    val level: String,
    @SerialName("@timestamp")
    val timestamp: Instant = Clock.System.now(),
    val mdc: Map<String, String>? = null,
    val context: Map<String, String>? = null,
    val contextName: String? = null,
)

fun ILoggingEvent.toLogMessage() = LogMessage(
    message = message,
    logger = loggerName,
    thread = threadName,
    level = level.levelStr,
    mdc = mdcPropertyMap.takeIf { (it?.size ?: 0) > 0 },
    contextName = loggerContextVO?.name,
    context = this.loggerContextVO?.propertyMap.takeIf { (it?.size ?: 0) > 0 }
)