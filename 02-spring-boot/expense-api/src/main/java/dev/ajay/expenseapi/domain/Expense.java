package dev.ajay.expenseapi.domain;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * The JPA entity (Phase 4). Compare the Java capstone's {@code Expense} record:
 * a JPA entity CAN'T be a record — Hibernate needs a no-arg constructor and
 * mutable fields to instantiate-then-populate via reflection and to build lazy
 * proxies (Java Phase 7.3). So the entity is a mutable class; we use records
 * for the DTOs at the web edge instead (Phase 2.3).
 */
@Entity
@Table(name = "expense")
public class Expense {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)   // DB auto-increment
    private Long id;

    @Column(nullable = false)
    private String description;

    @Column(nullable = false, precision = 12, scale = 2)  // exact money (NUMERIC)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)                          // store "FOOD", not ordinal
    @Column(nullable = false, length = 30)
    private Category category;

    @Column(name = "spent_on", nullable = false)
    private LocalDate spentOn;

    protected Expense() { }                               // required by JPA

    public Expense(String description, BigDecimal amount, Category category, LocalDate spentOn) {
        this.description = description;
        this.amount = amount;
        this.category = category;
        this.spentOn = spentOn;
    }

    public Long getId()             { return id; }
    public String getDescription()  { return description; }
    public BigDecimal getAmount()   { return amount; }
    public Category getCategory()   { return category; }
    public LocalDate getSpentOn()   { return spentOn; }

    public void setDescription(String description) { this.description = description; }
    public void setAmount(BigDecimal amount)       { this.amount = amount; }
    public void setCategory(Category category)     { this.category = category; }
    public void setSpentOn(LocalDate spentOn)      { this.spentOn = spentOn; }
}
