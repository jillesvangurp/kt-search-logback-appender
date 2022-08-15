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