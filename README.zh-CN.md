[English](README.md) | 简体中文

Compose 将 AMQP 和管理控制台绑定到本机回环地址；队列及演示凭据只供本地使用。

# 电子票渲染工作队列

运行 `./mvnw -q verify` 和 `docker compose up -d`。在多个终端运行 `./mvnw -q exec:java -Dexec.mainClass=com.elsworne.stubline.spike.rabbitmqtasks.RabbitTasksMain -Dexec.args='worker 1'`。在另一终端运行 `./mvnw -q exec:java -Dexec.mainClass=com.elsworne.stubline.spike.rabbitmqtasks.RabbitTasksMain -Dexec.args='produce order-1 order-2 fail-order-3'`。预期看到 `enqueued ...`，各 worker 对成功任务各打印一次 `rendered ...`。失败任务尝试三次，每次重试前延迟 500 毫秒，最终进入 `ticket.render.dead`。运行 `docker compose exec rabbitmq rabbitmqctl list_queues name messages messages_unacknowledged` 查看队列。最后运行 `docker compose down`。

worker 默认设置 `basicQos(1)`，渲染完成后才手动 ack。处理中的 worker 关闭后，未确认消息会重新投递给另一个 worker。失败时，worker 把持久消息连同递增的重试头发往 TTL 延迟队列，收到 broker 确认后才 ack 原消息。两次重试后发送到 dead-letter exchange 和 queue。`./mvnw -q -Pdocker verify` 测试 ack、worker 中途死亡、prefetch 分配、重试与死信。

| | RabbitMQ 工作队列 | Kafka 日志与 consumer group |
|---|---|---|
| 工作 | 每个任务交给一个 worker；ack 后删除 | 每个 group 读取所有记录；offset 记录进度 |
| 重投递 | 未 ack 消息返回队列 | 从未提交的 offset 重读 |
| 保留 | 直到 ack、过期或进入死信 | 由时间/大小策略决定，与消费无关 |
| 适用 | 独立任务及逐任务重试 | 有序事件历史与多个 consumer group |

## 许可证

本仓库采用 [MIT License](LICENSE)。

## 关于本仓库

本仓库包含一项针对 Stubline 的独立实验。README 说明运行命令与观察结果。

本仓库的代码与文档借助 AI 工具编写；记录的输出都来自实际运行。
