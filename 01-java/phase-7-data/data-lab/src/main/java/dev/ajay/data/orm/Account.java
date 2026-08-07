package dev.ajay.data.orm;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/*
 * Lesson 7.3 — a JPA ENTITY.
 *
 * JPA (Jakarta Persistence API) maps OBJECTS <-> TABLE ROWS so you work
 * with Java objects, not SQL strings. Hibernate is the implementation
 * that generates the SQL underneath.
 *
 * ANNOTATIONS are the mapping:
 *   @Entity        this class maps to a table
 *   @Id            the primary key field
 *   @GeneratedValue the DB assigns the id
 *   @Column        column-level details (name, nullability, precision)
 *
 * Note this is NOT a record: JPA entities need a no-arg constructor and
 * mutable fields (the framework constructs then populates them via
 * reflection) — one of the few places records don't fit.
 */
@Entity
@Table(name = "account")
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)   // maps to AUTO_INCREMENT
    private Long id;

    @Column(nullable = false)
    private String owner;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal balance;

    protected Account() { }        // JPA requires it; protected discourages misuse

    public Account(String owner, BigDecimal balance) {
        this.owner = owner;
        this.balance = balance;
    }

    public Long getId()          { return id; }
    public String getOwner()     { return owner; }
    public BigDecimal getBalance() { return balance; }
    public void setBalance(BigDecimal balance) { this.balance = balance; }

    @Override
    public String toString() {
        return "Account{id=" + id + ", owner=" + owner + ", balance=" + balance + "}";
    }
}
