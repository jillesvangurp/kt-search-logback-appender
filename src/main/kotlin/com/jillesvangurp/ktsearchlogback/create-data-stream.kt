package com.jillesvangurp.ktsearchlogback

import com.jillesvangurp.jsondsl.withJsonDsl
import com.jillesvangurp.ktsearch.*
import kotlinx.datetime.Clock
import kotlin.time.Duration

suspend fun SearchClient.dataStreamExists(name: String): Boolean {
    val response = restClient.get {
        path(name)
    }
    val e = response.exceptionOrNull()
    return if(e!=null) {
        if(e is RestException) {
            if(e.status == 404) {
                false
            } else {
                throw e
            }
        } else {
            throw e
        }
    } else {
        true
    }
}

suspend fun SearchClient.manageDataStream(
    prefix: String,
    hotRollOverGb:Int,
    numberOfReplicas: Int,
    numberOfShards: Int,
    warmMinAge: Duration,
    deleteMinAge: Duration,
    warmShrinkShards: Int,
    warmSegments: Int,
): Boolean {
    if(dataStreamExists(prefix)) {
        // don't recreate
        return false
    }
    setIlmPolicy("$prefix-ilm-policy") {
        hot {
            actions {
                rollOver(hotRollOverGb)
            }
        }
        warm {
            minAge(warmMinAge)
            actions {
                shrink(warmShrinkShards)
                forceMerge(warmSegments)
            }
        }
        delete {
            minAge(deleteMinAge)
            actions {
                delete()
            }
        }
    }
    // using component templates is a good idea
    updateComponentTemplate("$prefix-template-settings") {
        settings {
            replicas = numberOfReplicas
            shards = numberOfShards
            put("index.lifecycle.name", "$prefix-ilm-policy")
        }
    }
    updateComponentTemplate("$prefix-template-mappings") {
        dynamicTemplate("mdc_keywords") {
            pathMatch = "mdc.*"
            mapping("keyword")
        }
        dynamicTemplate("mdc_keywords") {
            pathMatch = "context.*"
            mapping("keyword")
        }
        mappings {
            text("text")
            text(LogMessage::message) {
                fields {
                    keyword("keyword")
                }
                copyTo = listOf("text")
            }
            date("@timestamp")
            keyword("thread")
            keyword("level")
            keyword("contextName")
        }
        meta {
            put("created_by","kt-search-logback-appender")
            put("created_at", Clock.System.now().toString())
        }
    }
    // now create the template
    createIndexTemplate("$prefix-template") {
        indexPatterns = listOf("$prefix*")
        // make sure to specify an empty object for data_stream
        dataStream = withJsonDsl {
            // the elastic docs are a bit vague on what goes here
        }
        // make sure we outrank elastics own stuff
        priority=300
        composedOf = listOf("$prefix-template-settings", "$prefix-template-mappings")
    }

    createDataStream(prefix)
    return true
}