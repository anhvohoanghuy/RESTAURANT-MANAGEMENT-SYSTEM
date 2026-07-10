package com.example.feat1.DDD.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.example.feat1.DDD.inventory_context.application.event.SettleTriggerEvent;
import com.example.feat1.DDD.kitchen_context.infrastructure.adapter.KafkaKitchenSettleTriggerPublisher;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Locks in the whenComplete send-failure logging (K-WR-01 / I-WR-03) added to the fire-and-forget
 * saga/kitchen Kafka publishers: a failed send is logged with an ERROR event containing the orderId
 * and the caller never sees an exception, while a successful send logs nothing. Broker-free —
 * KafkaTemplate is mocked, no EmbeddedKafka.
 */
class KafkaPublisherWhenCompleteTest {

  private ListAppender<ILoggingEvent> settleTriggerAppender;

  @BeforeEach
  void setUp() {
    settleTriggerAppender = attach(KafkaKitchenSettleTriggerPublisher.class);
  }

  @AfterEach
  void tearDown() {
    detach(KafkaKitchenSettleTriggerPublisher.class, settleTriggerAppender);
  }

  private static ListAppender<ILoggingEvent> attach(Class<?> adapterClass) {
    Logger logger = (Logger) LoggerFactory.getLogger(adapterClass);
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);
    return appender;
  }

  private static void detach(Class<?> adapterClass, ListAppender<ILoggingEvent> appender) {
    Logger logger = (Logger) LoggerFactory.getLogger(adapterClass);
    logger.detachAppender(appender);
    appender.stop();
  }

  @Test
  @SuppressWarnings("unchecked")
  void settleTriggerPublisherLogsErrorOnFailedSendAndDoesNotThrow() {
    KafkaTemplate<String, SettleTriggerEvent> kafkaTemplate = mock(KafkaTemplate.class);
    KafkaKitchenSettleTriggerPublisher publisher =
        new KafkaKitchenSettleTriggerPublisher(kafkaTemplate);
    ReflectionTestUtils.setField(publisher, "settleTriggerTopic", "kitchen.settlement-trigger");

    UUID orderId = UUID.randomUUID();
    SettleTriggerEvent event =
        new SettleTriggerEvent(
            UUID.randomUUID(),
            SettleTriggerEvent.TYPE,
            Instant.now(),
            orderId,
            UUID.randomUUID(),
            1);

    when(kafkaTemplate.send(anyString(), anyString(), any()))
        .thenReturn(CompletableFuture.failedFuture(new RuntimeException("broker down")));

    publisher.publishSettleTrigger(event);

    assertThat(settleTriggerAppender.list)
        .anySatisfy(
            e -> {
              assertThat(e.getLevel()).isEqualTo(Level.ERROR);
              assertThat(e.getFormattedMessage()).contains(orderId.toString());
            });
  }

  @Test
  @SuppressWarnings("unchecked")
  void settleTriggerPublisherLogsNothingOnSuccessfulSend() {
    KafkaTemplate<String, SettleTriggerEvent> kafkaTemplate = mock(KafkaTemplate.class);
    KafkaKitchenSettleTriggerPublisher publisher =
        new KafkaKitchenSettleTriggerPublisher(kafkaTemplate);
    ReflectionTestUtils.setField(publisher, "settleTriggerTopic", "kitchen.settlement-trigger");

    SettleTriggerEvent event =
        new SettleTriggerEvent(
            UUID.randomUUID(),
            SettleTriggerEvent.TYPE,
            Instant.now(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            1);

    when(kafkaTemplate.send(anyString(), anyString(), any()))
        .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));

    publisher.publishSettleTrigger(event);

    assertThat(settleTriggerAppender.list).noneMatch(e -> e.getLevel() == Level.ERROR);
  }
}
