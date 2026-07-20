package com.elsworne.stubline.spike.rabbitmqtasks;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeoutException;

/** Durable e-ticket work queue with manual ack, bounded retry, delay, and dead letters. */
public final class TaskQueue implements AutoCloseable {
  public static final String WORK = "ticket.render";
  public static final String DEAD = "ticket.render.dead";
  private final Connection connection;
  private final Channel channel;

  public TaskQueue(String host, int port) throws IOException, TimeoutException {
    var factory = new ConnectionFactory();
    factory.setHost(host);
    factory.setPort(port);
    connection = factory.newConnection();
    channel = connection.createChannel();
    channel.exchangeDeclare("render.retry", "direct", true);
    channel.exchangeDeclare("render.dead", "direct", true);
    channel.queueDeclare(WORK, true, false, false, Map.of());
    channel.queueDeclare(DEAD, true, false, false, Map.of());
    channel.queueBind(DEAD, "render.dead", "dead");
    channel.queueDeclare(
        "ticket.render.retry",
        true,
        false,
        false,
        Map.of(
            "x-message-ttl", 500, "x-dead-letter-exchange", "", "x-dead-letter-routing-key", WORK));
    channel.queueBind("ticket.render.retry", "render.retry", "retry");
    channel.confirmSelect();
  }

  public void enqueue(String job) throws Exception {
    channel.basicPublish(
        "",
        WORK,
        new AMQP.BasicProperties.Builder().deliveryMode(2).build(),
        job.getBytes(StandardCharsets.UTF_8));
    channel.waitForConfirmsOrDie(5000);
  }

  public long pending(String queue) throws IOException {
    return channel.queueDeclarePassive(queue).getMessageCount();
  }

  public Channel worker(int prefetch, JobHandler handler) throws IOException {
    var worker = connection.createChannel();
    worker.basicQos(prefetch);
    worker.confirmSelect();
    worker.basicConsume(
        WORK,
        false,
        (tag, delivery) -> {
          String job = new String(delivery.getBody(), StandardCharsets.UTF_8);
          try {
            handler.render(job);
            worker.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
          } catch (Exception failure) {
            int retries = 0;
            Object header =
                delivery.getProperties().getHeaders() == null
                    ? null
                    : delivery.getProperties().getHeaders().get("x-retry");
            if (header instanceof Number number) retries = number.intValue();
            boolean retry = retries < 2;
            var headers = new HashMap<String, Object>();
            headers.put("x-retry", retries + 1);
            var properties =
                new AMQP.BasicProperties.Builder().deliveryMode(2).headers(headers).build();
            try {
              worker.basicPublish(
                  retry ? "render.retry" : "render.dead",
                  retry ? "retry" : "dead",
                  properties,
                  delivery.getBody());
              worker.waitForConfirmsOrDie(5000);
              worker.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
            } catch (Exception publishFailure) {
              worker.basicNack(delivery.getEnvelope().getDeliveryTag(), false, true);
            }
          }
        },
        tag -> {});
    return worker;
  }

  /** Processes a render job. */
  public interface JobHandler {
    void render(String job) throws Exception;
  }

  @Override
  public void close() throws Exception {
    channel.close();
    connection.close();
  }
}
