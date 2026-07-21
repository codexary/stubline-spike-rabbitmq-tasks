English | [简体中文](README.zh-CN.md)

Compose binds AMQP and the management console to loopback because this queue and its demo credentials are local only.

# E-ticket rendering work queue

Run `./mvnw -q verify` and `docker compose up -d`. Start several terminals with `./mvnw -q exec:java -Dexec.mainClass=com.elsworne.stubline.spike.rabbitmqtasks.RabbitTasksMain -Dexec.args='worker 1'`. In another terminal, run `./mvnw -q exec:java -Dexec.mainClass=com.elsworne.stubline.spike.rabbitmqtasks.RabbitTasksMain -Dexec.args='produce order-1 order-2 fail-order-3'`. Expect `enqueued ...` lines and a single `rendered ...` line for each successful job, divided among workers. The failed job is attempted three times, with a 500 ms delay before each retry, then appears in `ticket.render.dead`. Inspect with `docker compose exec rabbitmq rabbitmqctl list_queues name messages messages_unacknowledged`. Stop with `docker compose down`.

Workers set `basicQos(1)` by default and acknowledge only after rendering. Closing a worker while a job is unacknowledged causes RabbitMQ to redeliver it to another worker. On failure the worker republishes a persistent message to a TTL retry queue with an incremented retry header and acknowledges the original only after broker confirmation. After two retries it publishes to a dead-letter exchange and queue. `./mvnw -q -Pdocker verify` tests ack, worker death, prefetch distribution, retries, and dead letters.

| | RabbitMQ work queue | Kafka log and consumer group |
|---|---|---|
| Work | One worker receives each queued job; ack removes it | Each group reads every record; offset tracks progress |
| Redelivery | Unacked message returns to queue | Re-read from uncommitted offset |
| Retention | Until ack or expiration/dead letter | By time/size policy, independent of consumption |
| Fit | Independent tasks and task-level retry | Ordered event history and multiple consumer groups |

## License

This repository is licensed under the [MIT License](LICENSE).

## About this repository

This repository contains a focused Stubline experiment. The README documents its commands and observed results.

The code and documents in this repository were written with the help of AI tools; every recorded output comes from actually running them.

## Findings

The experiment results and their effect on the backend decision are recorded in [FINDINGS.md](FINDINGS.md).
