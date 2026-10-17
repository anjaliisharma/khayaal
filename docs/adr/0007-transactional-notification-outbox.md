# ADR 0007: Transactional notification outbox with database claiming

## Context

Reminder planning runs repeatedly and multiple application instances may dispatch concurrently. Direct sends from scheduler code can duplicate messages or lose them when a process stops between database work and network delivery.

## Decision

Planners persist notifications with deterministic unique dedupe keys. Dispatchers claim bounded batches through PostgreSQL `FOR UPDATE SKIP LOCKED`, mark them `PROCESSING`, commit, and then invoke a channel adapter. Stale claims become reclaimable after five minutes. Delivery is at-least-once; successful rows become `SENT`, while failures use exponential backoff and eventually become `DEAD`.

Before delivery, dose notifications recheck the current dose status. A confirmation therefore suppresses queued due/follow-up messages. Payload text is materialized at planning time with discreet-mode rules, while logs and failure codes never contain medicine names.

## Alternatives

- Send inside the scheduler transaction: holds locks during network calls and loses retry state.
- Introduce Kafka/RabbitMQ: operationally excessive for the current deployment.
- In-memory queues: lose work on restart and cannot coordinate instances.

## Consequences

PostgreSQL is both the transaction boundary and durable queue. Delivery survives restarts and scales across instances, at the cost of polling and outbox retention work.
