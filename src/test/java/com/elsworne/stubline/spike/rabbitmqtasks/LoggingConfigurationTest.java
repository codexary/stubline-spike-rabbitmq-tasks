package com.elsworne.stubline.spike.rabbitmqtasks;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

/** Keeps RabbitMQ client chatter below the demo's logging threshold. */
class LoggingConfigurationTest {
  @Test
  void rabbitClientLogsAtInfoButNotDebug() {
    var logger = LoggerFactory.getLogger("com.rabbitmq.client");
    assertTrue(logger.isInfoEnabled());
    assertFalse(logger.isDebugEnabled());
    var root =
        (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
    assertTrue(root.iteratorForAppenders().hasNext());
  }
}
