package com.elsworne.stubline.spike.rabbitmqtasks;

import java.util.concurrent.CountDownLatch;

/** Producer and worker command-line demo. */
public final class RabbitTasksMain {
  private RabbitTasksMain() {}

  public static void main(String[] args) throws Exception {
    String host = System.getenv().getOrDefault("RABBITMQ_HOST", "localhost");
    try (var queue = new TaskQueue(host, 5672)) {
      if (args.length > 0 && args[0].equals("produce")) {
        for (int i = 1; i < args.length; i++) {
          queue.enqueue(args[i]);
          System.out.println("enqueued " + args[i]);
        }
      } else if (args.length > 0 && args[0].equals("worker")) {
        int prefetch = args.length > 1 ? Integer.parseInt(args[1]) : 1;
        queue.worker(
            prefetch,
            job -> {
              if (job.startsWith("fail")) throw new IllegalStateException("render failed");
              System.out.println("rendered " + job);
            });
        new CountDownLatch(1).await();
      } else throw new IllegalArgumentException("produce JOB... or worker [PREFETCH]");
    }
  }
}
