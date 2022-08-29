Log appender for Logback that pushes to Elasticsearch using kt-search as the client.

Note. work in progress and not ready for general usage yet


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

