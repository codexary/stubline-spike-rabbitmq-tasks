English | [简体中文](FINDINGS.zh-CN.md)

# Findings

## What we observed

The work queue gives one worker each rendering job. Workers acknowledge after rendering; closing a channel with an unacknowledged job returns it for redelivery. `basicQos(1)` limits unacknowledged work per worker. A failed job moves through a TTL retry queue, then to `ticket.render.dead` after three attempts. Delivery can repeat, so rendering must tolerate retries.

## Backend decision

RabbitMQ fits independent rendering tasks. An acknowledged message leaves the queue, whereas order events need retained history for separate consumers. The backend therefore uses an outbox and Kafka for order events. See [backend ADR 0010 — Use outbox and Kafka for order events](https://github.com/codexary/stubline-backend/blob/main/docs/adr/0010-use-outbox-and-kafka-for-order-events.md).
