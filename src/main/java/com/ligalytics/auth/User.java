package com.ligalytics.auth;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Cuenta de la demostración. Solo guarda el correo, un nombre, el hash de la contraseña y el saldo ficticio
 * (pesos colombianos de mentira): no hay dinero real en ningún punto de la aplicación.
 */
@Entity
@Table(name = "app_users")
@Getter
@Setter
@NoArgsConstructor
public class User {

    /** Saldo ficticio con el que empieza cada cuenta (COP de demostración). */
    public static final long INITIAL_BALANCE = 100_000L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 160)
    private String email;

    @Column(name = "display_name", nullable = false, length = 80)
    private String displayName;

    @Column(name = "password_hash", nullable = false, length = 200)
    private String passwordHash;

    /** Saldo ficticio en pesos colombianos (enteros). */
    @Column(nullable = false)
    private long balance = INITIAL_BALANCE;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}
