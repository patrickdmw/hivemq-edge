# Building code

To build the docker image, run
```bash
./gradlew -Dorg.gradle.java.home=/home/patrickd/.jdks/corretto-26.0.1 loadOciImage
```

if that fails with unexpected errors, also update the other git repos (in the same parent as this one)
- hivemq-edge-adapter-sdk/
- hivemq-edge-extension-sdk/

to the same tag version and retry.

When `loadOciImage` works, but running in IntelliJ does not and fails with unexpected errors (mostly class loaders) run
```bash
./gradlew -Dorg.gradle.java.home=/home/patrickd/.jdks/corretto-26.0.1 :hivemq-edge-module-plc4x:copyAllDependencies
```
and again for other modules that might require rebuilding. Especially important after switching branches with different versions of that library.

also checkout the [build](docker/build.sh) and [push](docker/push.sh) scripts for detailed commands to build the image


# Errors

org.apache.plc4x.java.api.exceptions.PlcProtocolException: Unsupported tag type null
    at org.apache.plc4x.java.s7.S7CotpConnection.onRead(S7CotpConnection.java:406)
    at org.apache.plc4x.java.spi.drivers.ConnectionBase.read(ConnectionBase.java:313)
    at org.apache.plc4x.java.spi.drivers.messages.DefaultPlcReadRequest.execute(DefaultPlcReadRequest.java:50)
    at com.hivemq.edge.adapters.plc4x.impl.Plc4xConnection.read(Plc4xConnection.java:179)
    at com.hivemq.edge.adapters.plc4x.impl.AbstractPlc4xAdapter.poll(AbstractPlc4xAdapter.java:111)
    at com.hivemq.protocols.northbound.PerAdapterSampler.execute(PerAdapterSampler.java:61)
    at com.hivemq.edge.modules.adapters.impl.polling.PollingTask.run(PollingTask.java:84)
    at java.base/java.util.concurrent.Executors$RunnableAdapter.call(Executors.java:545)
    at java.base/java.util.concurrent.FutureTask.run$$$capture(FutureTask.java:328)
    at java.base/java.util.concurrent.FutureTask.run(FutureTask.java)
    at java.base/java.util.concurrent.ScheduledThreadPoolExecutor$ScheduledFutureTask.run(ScheduledThreadPoolExecutor.java:309)
    at java.base/java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1090)
    at java.base/java.util.concurrent.ThreadPoolExecutor$Worker.run(ThreadPoolExecutor.java:614)
    at java.base/java.lang.Thread.run(Thread.java:1474)


plc4j/spi/drivers/src/main/java/org/apache/plc4x/java/spi/drivers/messages/DefaultPlcReadRequest.java#addTagAddress
should log
log.error("Failed to add tag address '{}' for name '{}' because 'PlcResponseCode.INVALID_ADDRESS'", tagAddress, name, e);

%DB23:15:STRING(32)
