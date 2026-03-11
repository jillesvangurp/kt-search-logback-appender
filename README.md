[![Run tests](https://github.com/jillesvangurp/kt-search-logback-appender/actions/workflows/ci.yml/badge.svg)](https://github.com/jillesvangurp/kt-search-logback-appender/actions/workflows/ci.yml)

Log appender for Logback that bulk indexes to Elasticsearch using kt-search as the client.

The appender uses the provided `LogMessage` as a model class. This is an opinionated representation of log messages as produced by Logback. It passes on MDC variable, allows you pass on Logback context variables, and represents exception stack traces in a way that makes searching on exception classes or messages really easy.

Features

- Can be easily configured to work with elastic cloud
- You can also use Opensearch; create and manage templates/policies outside the appender (for example with `create-ds-and-template.sh`).
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

var dataStreamName = "applogs"
var contextVariableFilterRe = ""

/** comma separated list of mdc fields (without mdc prefix), will be coerced to Long in the json */
var coerceMdcFieldsToLong = ""
var coerceMdcFieldsToDouble = ""
```

## Elastic Cloud Privileges Needed

The appender only writes documents. If you create templates/policies/data streams outside the appender, grant those management permissions to that provisioning user or script.

For the runtime appender user, grant at least:

Cluster:
- monitor

Index:

Grant these permissions on the `applogs*` prefix

- create
- index
- create_doc
- create_index
- write
- view_index_metadata
