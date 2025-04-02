[![Run tests](https://github.com/jillesvangurp/kt-search-logback-appender/actions/workflows/ci.yml/badge.svg)](https://github.com/jillesvangurp/kt-search-logback-appender/actions/workflows/ci.yml)

Log appender for Logback that bulk indexes to Elasticsearch using kt-search as the client.

The appender uses the provided `LogMessage` as a model class. This is an opinionated representation of log messages as produced by Logback. It passes on MDC variable, allows you pass on Logback context variables, and represents exception stack traces in a way that makes searching on exception classes or messages really easy.

Features

- You can let it create a datastream for you; or if you prefer you can manage your own mapping templates
- Can be easily configured to work with elastic cloud
- You can also use Opensearch but in that case you may need to turn ilm off. You may want to explore the Opensearch state management for this which implements similar functionality.
- Supports various ways to provide MDC and other context to along with the log messages. This makes it easy to creat dashboards in e.g. Kibana and break things down by server, data center, instance type, and other meta data.

We've been using this appender for several years at [FORMATION](https://tryformation.com). We have a cheap Elastic Cloud logging cluster and our API server (Spring Boot) uses this plugin to send logging events there.

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
    implementation("com.jillesvangurp:kt-search-logback-appender:x.y.z")
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

The plugin can be configured via the normal mechanisms provided by Logback.

This is the list of writable properties and their defaults. Mostly, the properties are pretty self explanatory. You can override any of these with the appropriate xml tag in your logback configuration.

```kotlin
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
```

## Elastic Cloud Privileges Needed

When using the appender with elastic cloud and the `manageDataStreamAndTemplates` setting enabled you can either use a user with full privileges or create a user with at least these privileges:

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

