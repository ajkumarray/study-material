package dev.ajay.expenseapi.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A registered user (Phase 7.2). Named {@code AppUser} to avoid clashing with
 * Spring Security's own {@code User}. The {@code password} column stores a
 * BCrypt HASH — never plaintext (Phase 7 golden rule).
 */
@Entity
@Table(name = "app_user")
public class AppUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String username;

    @Column(nullable = false)
    private String password;              // BCrypt hash

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    protected AppUser() { }               // for JPA

    public AppUser(String username, String password, Role role) {
        this.username = username;
        this.password = password;
        this.role = role;
    }

    public Long getId()       { return id; }
    public String getUsername() { return username; }
    public String getPassword() { return password; }
    public Role getRole()     { return role; }
}
