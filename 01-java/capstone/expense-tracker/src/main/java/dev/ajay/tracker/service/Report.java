package dev.ajay.tracker.service;

import java.math.BigDecimal;
import java.util.Map;

import dev.ajay.tracker.domain.Category;
import dev.ajay.tracker.domain.Expense;

/**
 * An immutable snapshot of spending analytics (Phase 2.6 record, 4.2 streams).
 *
 * @param total         sum of all expense amounts
 * @param count         number of expenses
 * @param byCategory    total per category, highest first (insertion-ordered map)
 * @param largest       the single biggest expense, or null if none
 * @param averageAmount mean expense amount
 */
public record Report(BigDecimal total, long count,
                     Map<Category, BigDecimal> byCategory,
                     Expense largest, BigDecimal averageAmount) {

    /** Human-readable multi-line summary for the console. */
    public String render() {
        var sb = new StringBuilder();
        sb.append("Total spent : ").append(total).append('\n');
        sb.append("Expenses    : ").append(count).append('\n');
        sb.append("Average     : ").append(averageAmount).append('\n');
        sb.append("Largest     : ")
          .append(largest == null ? "-"
                  : largest.description() + " (" + largest.amount() + ")").append('\n');
        sb.append("By category :");
        if (byCategory.isEmpty()) {
            sb.append(" -");
        } else {
            byCategory.forEach((cat, amt) ->
                    sb.append("\n  ").append(String.format("%-13s %s", cat, amt)));
        }
        return sb.toString();
    }
}
