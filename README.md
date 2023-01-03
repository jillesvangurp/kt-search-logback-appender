[![Run tests](https://github.com/jillesvangurp/kt-search-logback-appender/actions/workflows/ci.yml/badge.svg)](https://github.com/jillesvangurp/kt-search-logback-appender/actions/workflows/ci.yml)

Log appender for Logback that bulk indexes to Elasticsearch using kt-search as the client.

The appender uses the provided `LogMessage` as a model class. This is an opinionated representation of log messages as produced by Logback. It passes on MDC variable, allows you pass on Logback context variables, and represents exception stack traces in a way that makes searching on exception classes or messages really easy.

Features

- You can let it create a datastream for you; or if you prefer you can manage your own mapping templates
- Can be easily configured to work with elastic cloud
- You can also use Opensearch but in that case you need to turn ilm off. You may want to explore the Opensearch state management for this which implements similar functionality.

Note. still a work in progress and not ready for general usage yet. 

## Gradle

Add the `maven.tryformation.com` repository:

```kotlin
repositories {
    mavenCentral()
    maven("https://maven.tryformation.com/releases") {
        content {
            includeGroup("com.jillesvangurp")
        }
    }
}
```

And then the dependency:

```kotlin
    // check the latest release tag for the current version
    implementation("com.jillesvangurp:kt-search-logback-appender:0.1.4")
```

## Usage

After adding the dependency, add the appender to your logback configuration.

```xml
<?xml version="1.0"?>
<configuration debug="false">
    <contextName>test-loggers</contextName>

    <!-- will get added to the context and logged along with everything-->
    <variable scope="context" name="environment" value="tests" />
    <variable scope="context" name="host" value="${HOSTNAME}" />

    <contextListener class="ch.qos.logback.classic.jul.LevelChangePropagator">
        <resetJUL>true</resetJUL>
    </contextListener>

    <appender name="es-out" class="com.jillesvangurp.ktsearchlogback.KtSearchLogBackAppender">
        <port>9999</port>
    </appender>
    
    <root level="INFO">
        <appender-ref ref="ASYNC-STDOUT"/>
        <appender-ref ref="es-out"/>
    </root>
</configuration>
```

## Settings

This is the list of writable properties and their defaults. TODO document properly but mostly does what it says on the tin. You can override any of these with the appropriate xml tag.

```kotlin
    var verbose = false
    var logElasticSearchCalls = false
    var host: String = "localhost"
    var port: Int = 9200
    var userName: String? = null
    var password: String? = null
    var ssl: Boolean = false

    var flushSeconds: Int = 1
    var bulkMaxPageSizw: Int = 200
    var createDataStream: Boolean = false
    // Elasticsearch only feature, leave disabled for opensearch
    var configureIlm = false

    var dataStreamName = "applogs"
    var hotRollOverGb = 2
    var numberOfReplicas = 1
    var numberOfShards = 1
    var warmMinAgeDays = 3
    var deleteMinAgeDays = 30
    var warmShrinkShards = 1
    var warmSegments = 1
```

## Elastic Cloud

When using the appender with elastic cloud and the `createDataStream` setting enabled you can either use a user with full privileges or create a user with at least these privileges:

Cluster:

- manage_index_templates
- manage_pipeline
- monitor
- manage_ilm

Index:

Grant these permissions on the `applogs*` prefix

- create
- index
- create_doc
- create_index
- write
- view_index_metadata

