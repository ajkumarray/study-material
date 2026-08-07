// Consumes expense events for an analytics/read-model service — completely decoupled
// from the producer (the API doesn't know or wait for this).
package com.example.expense.events;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
public class ExpenseEventConsumer {

    // One @KafkaListener per consumer group member. Kafka assigns partitions across the
    // group's instances (parallelism); a rebalance reassigns them if an instance joins/dies.
    @KafkaListener(topics = "expense-events", groupId = "expense-analytics")
    public void onEvent(ConsumerRecord<String, ExpenseEvent> record, Acknowledgment ack) {
        ExpenseEvent event = record.value();

        // Processing must be IDEMPOTENT: Kafka is at-least-once by default, so the same
        // event can arrive more than once (e.g. after a rebalance before commit). Use the
        // event id / an idempotency key so reprocessing is a no-op (System Design Ph.4).
        updateRunningTotals(event);   // e.g. upsert into a materialized analytics table

        // Commit the offset ONLY after successful processing (manual ack). If we crash
        // before this, the record is redelivered — hence the idempotency requirement.
        ack.acknowledge();
    }

    private void updateRunningTotals(ExpenseEvent event) { /* ... */ }
}
