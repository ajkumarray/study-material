package com.example.expense.events;

// An immutable domain event (Java record). Events describe something that HAPPENED
// (past tense) — the unit of an event-driven system.
public record ExpenseEvent(
    String eventId,     // unique id — the idempotency key for de-duplication
    String type,        // "created" | "updated" | "deleted"
    String userId,      // used as the Kafka message key → partition (ordering per user)
    long expenseId,
    long amountCents,
    long occurredAt     // epoch millis
) {}
