/*
 * Copyright 2019-present HiveMQ GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.hivemq.bridge.mqtt;

import com.google.common.util.concurrent.ListenableFuture;
import com.hivemq.api.mqtt.PublishReturnCode;
import com.hivemq.bridge.BridgeConstants;
import com.hivemq.bridge.config.MqttBridge;
import com.hivemq.bridge.config.RemoteSubscription;
import com.hivemq.bridge.metrics.PerBridgeMetrics;
import com.hivemq.client.mqtt.mqtt3.message.publish.Mqtt3Publish;
import com.hivemq.common.topic.TopicFilterProcessor;
import com.hivemq.configuration.HivemqId;
import com.hivemq.mqtt.message.QoS;
import com.hivemq.mqtt.message.publish.PUBLISH;
import com.hivemq.mqtt.message.publish.PUBLISHFactory;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

class RemotePublishConsumer implements Consumer<Mqtt3Publish> {
    private static final @NotNull Logger log = LoggerFactory.getLogger(RemotePublishConsumer.class);

    private final @NotNull HivemqId hivemqId;
    private final @NotNull MqttBridge bridge;
    private final @NotNull RemoteSubscription remoteSubscription;
    private final @NotNull BridgeInterceptorHandler bridgeInterceptorHandler;
    private final @NotNull ExecutorService executorService;
    private final @NotNull PerBridgeMetrics perBridgeMetrics;

    public RemotePublishConsumer(
            final @NotNull RemoteSubscription remoteSubscription,
            final @NotNull BridgeInterceptorHandler bridgeInterceptorHandler,
            final @NotNull MqttBridge bridge,
            final @NotNull ExecutorService executorService,
            final @NotNull HivemqId hivemqId,
            final @NotNull PerBridgeMetrics perBridgeMetrics) {
        this.remoteSubscription = remoteSubscription;
        this.bridgeInterceptorHandler = bridgeInterceptorHandler;
        this.bridge = bridge;
        this.executorService = executorService;
        this.hivemqId = hivemqId;
        this.perBridgeMetrics = perBridgeMetrics;
    }

    private static int extractHopCount(final @NotNull MqttBridge bridge, final @NotNull Mqtt3Publish mqtt3Publish) {
        return 0;
    }

    private static @NotNull PUBLISH convertPublish(
            final @NotNull String hivemqId,
            final @NotNull MqttBridge bridge,
            final @NotNull RemoteSubscription remoteSubscription,
            final @NotNull Mqtt3Publish mqtt3Publish,
            final int hopCount) {
        final QoS qos = Objects.requireNonNullElse(QoS.valueOf(Math.min(mqtt3Publish.getQos().getCode(),
                remoteSubscription.getMaxQoS())), QoS.AT_MOST_ONCE);
        return new PUBLISHFactory.Mqtt3Builder().withHivemqId(hivemqId)
                .withTopic(TopicFilterProcessor.modifyTopic(remoteSubscription.getDestination(),
                        mqtt3Publish.getTopic(),
                        Map.of(BridgeConstants.BRIDGE_NAME_TOPIC_REPLACEMENT_TOKEN, bridge.getId())).toString())
                .withQoS(qos)
                .withOnwardQos(qos)
                .withPayload(mqtt3Publish.getPayloadAsBytes())
                .withRetain(remoteSubscription.isPreserveRetain() && mqtt3Publish.isRetain())
                .build();
    }

    @Override
    public void accept(final @NotNull Mqtt3Publish mqtt3Publish) {
        try {
            perBridgeMetrics.getPublishRemoteReceivedCounter().inc();

            if (log.isTraceEnabled()) {
                log.trace("Received remote message on topic '{}' with QoS {} for bridge '{}'",
                        mqtt3Publish.getTopic(),
                        mqtt3Publish.getQos(),
                        bridge.getId());
            }

            final int hopCount = extractHopCount(bridge, mqtt3Publish);
            if (bridge.isLoopPreventionEnabled() && hopCount > 0 && hopCount >= bridge.getLoopPreventionHopCount()) {
                perBridgeMetrics.getLoopPreventionRemoteDropCounter().inc();
                if (log.isDebugEnabled()) {
                    log.debug("Remote message on topic '{}' ignored for bridge '{}', hop count {} >= max {}",
                            mqtt3Publish.getTopic(),
                            bridge.getId(),
                            hopCount,
                            bridge.getLoopPreventionHopCount());
                }
                return;
            }

            final long conversionStartTime = log.isDebugEnabled() ? System.nanoTime() : 0;
            final PUBLISH publish = convertPublish(hivemqId.get(), bridge, remoteSubscription, mqtt3Publish, hopCount);

            if (log.isDebugEnabled()) {
                final long conversionMicros = (System.nanoTime() - conversionStartTime) / 1000;
                log.debug("Converted remote message on topic '{}' to local format in {} μs for bridge '{}'",
                        mqtt3Publish.getTopic(),
                        conversionMicros,
                        bridge.getId());
            }

            final long interceptorStartTime = log.isDebugEnabled() ? System.nanoTime() : 0;
            final ListenableFuture<PublishReturnCode> publishFuture =
                    bridgeInterceptorHandler.interceptOrDelegateInbound(publish, executorService, bridge);
            publishFuture.addListener(() -> {
                try {
                    final PublishReturnCode returnCode = publishFuture.get();

                    if (log.isDebugEnabled()) {
                        final long totalMicros = (System.nanoTime() - interceptorStartTime) / 1000;
                        log.debug(
                                "Interceptor and publish completed in {} μs with result {} for topic '{}' on bridge '{}'",
                                totalMicros,
                                returnCode,
                                publish.getTopic(),
                                bridge.getId());
                    }

                    switch (returnCode) {
                        case DELIVERED -> {
                            perBridgeMetrics.getPublishLocalSuccessCounter().inc();
                            if (log.isTraceEnabled()) {
                                log.trace(
                                        "Remote message on topic '{}' successfully delivered to local subscribers for bridge '{}'",
                                        publish.getTopic(),
                                        bridge.getId());
                            }
                        }
                        case NO_MATCHING_SUBSCRIBERS -> {
                            perBridgeMetrics.getPublishLocalSuccessCounter().inc();
                            perBridgeMetrics.getPublishLocalNoSubscriberCounter().inc();
                            if (log.isDebugEnabled()) {
                                log.debug(
                                        "Remote message on topic '{}' published locally but no matching subscribers for bridge '{}'",
                                        publish.getTopic(),
                                        bridge.getId());
                            }
                        }
                        case FAILED -> {
                            perBridgeMetrics.getPublishLocalFailCounter().inc();
                            log.warn("Failed to publish remote message on topic '{}' to local broker for bridge '{}'",
                                    publish.getTopic(),
                                    bridge.getId());
                        }
                    }
                } catch (final InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.error("Interrupted while publishing remote message on topic '{}' for bridge '{}': {}",
                            publish.getTopic(),
                            bridge.getId(),
                            e.getMessage());
                    log.debug("Interrupt exception details", e);
                    perBridgeMetrics.getPublishLocalFailCounter().inc();
                } catch (final ExecutionException e) {
                    log.error("Failed to publish remote message on topic '{}' for bridge '{}': {}",
                            publish.getTopic(),
                            bridge.getId(),
                            e.getMessage());
                    log.debug("Execution exception details", e);
                    perBridgeMetrics.getPublishLocalFailCounter().inc();
                }
            }, executorService);
        } catch (final Throwable e) {
            perBridgeMetrics.getPublishLocalFailCounter().inc();
            log.error("Failed to process remote message on topic '{}' for bridge '{}': {}",
                    mqtt3Publish.getTopic(),
                    bridge.getId(),
                    e.getMessage());
            log.debug("Exception details for remote message processing", e);
        }
    }
}
