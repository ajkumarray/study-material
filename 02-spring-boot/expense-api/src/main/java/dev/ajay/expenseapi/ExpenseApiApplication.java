package dev.ajay.expenseapi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * The application entry point (Phase 1.2).
 *
 * <p>{@code @SpringBootApplication} is three annotations in one:
 * <ul>
 *   <li>{@code @SpringBootConfiguration} — this class defines beans/config</li>
 *   <li>{@code @EnableAutoConfiguration} — Spring configures sensible defaults
 *       based on what's on the classpath (web starter → embedded Tomcat + MVC;
 *       data-jpa → Hibernate + a DataSource). THIS is the "Boot" magic.</li>
 *   <li>{@code @ComponentScan} — discover {@code @Component}/{@code @Service}/
 *       {@code @RestController} beans in this package and below.</li>
 * </ul>
 *
 * <p>{@code SpringApplication.run} starts the IoC container, auto-configures
 * everything, and launches the embedded web server. Compare the Java capstone's
 * {@code App.main}, which wired every layer by hand — Spring does that wiring
 * via the container now.
 */
@SpringBootApplication
public class ExpenseApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(ExpenseApiApplication.class, args);
    }
}
