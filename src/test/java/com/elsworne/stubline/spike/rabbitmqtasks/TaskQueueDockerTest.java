package com.elsworne.stubline.spike.rabbitmqtasks;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.testcontainers.rabbitmq.RabbitMQContainer;

/** Broker tests for acknowledgement, redelivery, prefetch, retry, and dead letters. */
@Tag("docker")
@EnabledIfSystemProperty(named = "run.docker", matches = "true")
class TaskQueueDockerTest {
  private static RabbitMQContainer broker() {
    var broker = new RabbitMQContainer("rabbitmq:4.3.6-management-alpine");
    broker.start();
    return broker;
  }

  @Test
  void ackRemovesJob() throws Exception {
    try (var broker = broker();
        var queue = new TaskQueue(broker.getHost(), broker.getMappedPort(5672))) {
      var handled = new CountDownLatch(1);
      queue.worker(1, job -> handled.countDown());
      queue.enqueue("order-1");
      assertTrue(handled.await(5, TimeUnit.SECONDS));
      Thread.sleep(100);
      assertEquals(0, queue.pending(TaskQueue.WORK));
    }
  }

  @Test
  void redeliveredWhenWorkerDiesMidJob() throws Exception {
    try (var broker = broker();
        var queue = new TaskQueue(broker.getHost(), broker.getMappedPort(5672))) {
      var entered = new CountDownLatch(1);
      var release = new CountDownLatch(1);
      var again = new CountDownLatch(1);
      var first =
          queue.worker(
              1,
              job -> {
                entered.countDown();
                release.await();
              });
      queue.enqueue("order-2");
      assertTrue(entered.await(5, TimeUnit.SECONDS));
      first.abort();
      release.countDown();
      queue.worker(1, job -> again.countDown());
      assertTrue(again.await(5, TimeUnit.SECONDS));
    }
  }

  @Test
  void prefetchOneDistributesWhileFirstWorkerBlocked() throws Exception {
    try (var broker = broker();
        var queue = new TaskQueue(broker.getHost(), broker.getMappedPort(5672))) {
      var entered = new CountDownLatch(1);
      var release = new CountDownLatch(1);
      var second = new CountDownLatch(1);
      queue.worker(
          1,
          job -> {
            entered.countDown();
            release.await();
          });
      queue.enqueue("slow");
      assertTrue(entered.await(5, TimeUnit.SECONDS));
      queue.worker(1, job -> second.countDown());
      queue.enqueue("fast");
      assertTrue(second.await(5, TimeUnit.SECONDS));
      release.countDown();
    }
  }

  @Test
  void failureRetriesTwiceThenDeadLetters() throws Exception {
    try (var broker = broker();
        var queue = new TaskQueue(broker.getHost(), broker.getMappedPort(5672))) {
      var attempts = new java.util.concurrent.atomic.AtomicInteger();
      queue.worker(
          1,
          job -> {
            attempts.incrementAndGet();
            throw new IllegalStateException("render");
          });
      queue.enqueue("fail-order");
      for (int i = 0; i < 30 && queue.pending(TaskQueue.DEAD) == 0; i++) Thread.sleep(200);
      assertEquals(3, attempts.get());
      assertEquals(1, queue.pending(TaskQueue.DEAD));
    }
  }
}
