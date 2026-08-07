// Publishes domain events to a Kafka topic when expenses change. Other services
// (analytics, notifications) consume them — the app is decoupled from its consumers.
package com.example.expense.events;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class ExpenseEventProducer {

    private static final String TOPIC = "expense-events";
    private final KafkaTemplate<String, ExpenseEvent> kafka;

    public ExpenseEventProducer(KafkaTemplate<String, ExpenseEvent> kafka) {
        this.kafka = kafka;
    }

    // The KEY (here the userId) decides the PARTITION: all events for one user go to the
    // same partition, which guarantees their ORDER is preserved on the consumer side.
    public void publish(ExpenseEvent event) {
        kafka.send(TOPIC, event.userId(), event);   // (topic, key, value)
    }
}
