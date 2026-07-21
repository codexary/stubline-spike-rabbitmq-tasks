[English](FINDINGS.md) | 简体中文

# 实验结论

## 观察

工作队列把一份渲染任务交给一名 worker。worker 完成后才确认；连接在确认前关闭，任务会重新投递。`basicQos(1)` 限制每名 worker 的未确认任务。失败任务经过 TTL 重试队列，三次仍失败就进入 `ticket.render.dead`。投递可能重复，渲染必须能安全重做。

## 对后端的影响

RabbitMQ 适合独立的渲染任务。消息确认后就离开队列，而订单事件需要留存，供不同消费者各自读取。后端因此采用 outbox 与 Kafka 传递订单事件。参见[后端 ADR 0010：Use outbox and Kafka for order events](https://github.com/codexary/stubline-backend/blob/main/docs/adr/0010-use-outbox-and-kafka-for-order-events.md)。
