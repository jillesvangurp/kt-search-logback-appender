# Deprecated: use the KT Search Logback module

This standalone project is deprecated in favor of
[`kt-search-lib-logback`](https://github.com/jillesvangurp/kt-search/tree/master/kt-search-lib-logback)
in KT Search. The module is built and released with KT Search and uses the
same version, keeping the appender and client APIs aligned.

Replace `com.jillesvangurp:kt-search-logback-appender` with
`com.jillesvangurp:kt-search-lib-logback` using the same release version as
your other KT Search modules. Remove the old artifact, including transitive
copies, to avoid duplicate classes. Package names and Logback XML settings
are unchanged. The new module requires Java 17 or newer.

See the [module documentation](https://github.com/jillesvangurp/kt-search/tree/master/kt-search-lib-logback)
for setup, configuration, and the provisioning script. The new coordinates
will be available starting with the first KT Search release containing the
module. The instructions below describe the legacy standalone releases.

---

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

Before using the appender, create the data stream and templates once. The appender writes documents but does not provision Elasticsearch resources for you at runtime.

The helper script creates the data stream, component templates, index template, mappings, and optionally the ILM policy:

```bash
./create-ds-and-template.sh --es-url http://localhost:9200 --data-stream-name applogs
```

The verification step in the script uses `jq`, so make sure `curl` and `jq` are installed.

After provisioning, add the appender to your Logback configuration.

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

    <appender name="STDOUT" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>%d %-5level %logger - %msg%n</pattern>
        </encoder>
    </appender>

    <appender name="ASYNC-STDOUT" class="ch.qos.logback.classic.AsyncAppender" queueSize="2048">
        <appender-ref ref="STDOUT"/>
    </appender>

    <appender name="es-out" class="com.jillesvangurp.ktsearchlogback.KtSearchLogBackAppender">
        <host>localhost</host>
        <port>9999</port>
        <dataStreamName>applogs</dataStreamName>
    </appender>

    <root level="INFO">
        <appender-ref ref="ASYNC-STDOUT"/>
        <appender-ref ref="es-out"/>
    </root>
</configuration>
```

For a remote or Elastic Cloud cluster, configure the connection explicitly:

```xml
<appender name="es-out" class="com.jillesvangurp.ktsearchlogback.KtSearchLogBackAppender">
    <host>your-deployment.es.region.provider.cloud.es.io</host>
    <port>443</port>
    <ssl>true</ssl>
    <userName>elastic</userName>
    <password>${ELASTIC_PASSWORD}</password>
    <dataStreamName>applogs</dataStreamName>
</appender>
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
var bulkMaxPageSize: Int = 200

var dataStreamName = "applogs"
var contextVariableFilterRe = ""

/** comma separated list of mdc fields (without mdc prefix), will be coerced to Long in the json */
var coerceMdcFieldsToLong = ""
var coerceMdcFieldsToDouble = ""
```

## Indexed Fields

The default index template created by the helper script maps these fields:

- `@timestamp`
- `message`
- `thread`
- `level`
- `logger`
- `contextName`
- `mdc.*`
- `context.*`
- `exceptionList.className`
- `exceptionList.message`

String fields inside `mdc` and `context` are mapped dynamically as keywords so they can be used directly in filters and aggregations.

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
