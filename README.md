gradle :hivemq-edge-module-plc4x:copyAllDependencies


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
