package com.ligalytics.auth;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ligalytics.exception.UnauthorizedException;

/**
 * Registro, inicio y cierre de sesión de la demostración. Contraseñas con PBKDF2, tokens de sesión opacos
 * (solo se guarda su hash) que caducan a los 7 días y bloqueo temporal tras 5 intentos fallidos por correo.
 *
 * <p>Es una cuenta de demostración: no hay verificación de correo ni recuperación de contraseña, y el
 * único "dinero" que existe es el saldo ficticio.</p>
 */
@Service
public class AuthService {

    /** Cuenta y token recién creados. */
    public record AuthResult(String token, User user) {
    }

    static final Duration SESSION_LIFETIME = Duration.ofDays(7);
    private static final int MAX_FAILURES = 5;
    private static final Duration LOCK_TIME = Duration.ofMinutes(5);
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]{2,}$");

    private record Failures(int count, Instant lockedUntil) {
    }

    private final UserRepository users;
    private final AuthSessionRepository sessions;
    private final PasswordHasher hasher;
    private final SecureRandom random = new SecureRandom();
    private final ConcurrentMap<String, Failures> failures = new ConcurrentHashMap<>();
    private final String dummyHash;

    public AuthService(UserRepository users, AuthSessionRepository sessions, PasswordHasher hasher) {
        this.users = users;
        this.sessions = sessions;
        this.hasher = hasher;
        this.dummyHash = hasher.hash("contrasena-de-relleno");
    }

    @Transactional
    public AuthResult register(String email, String displayName, String password) {
        String normalized = normalizeEmail(email);
        if (!EMAIL.matcher(normalized).matches() || normalized.length() > 160) {
            throw new IllegalArgumentException("El correo no es válido");
        }
        String name = displayName == null ? "" : displayName.trim();
        if (name.length() < 2 || name.length() > 40) {
            throw new IllegalArgumentException("El nombre debe tener entre 2 y 40 caracteres");
        }
        if (password == null || password.length() < 8 || password.length() > 100) {
            throw new IllegalArgumentException("La contraseña debe tener entre 8 y 100 caracteres");
        }
        if (users.findByEmailIgnoreCase(normalized).isPresent()) {
            throw new IllegalArgumentException("Ese correo ya está registrado");
        }
        User user = new User();
        user.setEmail(normalized);
        user.setDisplayName(name);
        user.setPasswordHash(hasher.hash(password));
        user = users.save(user);
        return new AuthResult(openSession(user), user);
    }

    @Transactional
    public AuthResult login(String email, String password) {
        String normalized = normalizeEmail(email);
        Failures current = failures.get(normalized);
        if (current != null && current.lockedUntil() != null && Instant.now().isBefore(current.lockedUntil())) {
            throw new UnauthorizedException("Demasiados intentos fallidos: espera unos minutos");
        }
        Optional<User> user = users.findByEmailIgnoreCase(normalized);
        // Se calcula siempre un hash para que la respuesta tarde lo mismo exista o no el correo.
        boolean valid = hasher.matches(password == null ? "" : password,
                user.map(User::getPasswordHash).orElse(dummyHash)) && user.isPresent();
        if (!valid) {
            int count = current == null ? 1 : current.count() + 1;
            failures.put(normalized, new Failures(count, count >= MAX_FAILURES ? Instant.now().plus(LOCK_TIME) : null));
            throw new UnauthorizedException("Correo o contraseña incorrectos");
        }
        failures.remove(normalized);
        return new AuthResult(openSession(user.get()), user.get());
    }

    @Transactional
    public void logout(String token) {
        if (token != null && !token.isBlank()) {
            sessions.deleteByTokenHash(PasswordHasher.sha256(token));
        }
    }

    /** Usuario dueño del token, si la sesión existe y no ha caducado. */
    @Transactional
    public Optional<User> authenticate(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        Optional<AuthSession> session = sessions.findByTokenHash(PasswordHasher.sha256(token));
        if (session.isEmpty()) {
            return Optional.empty();
        }
        if (session.get().getExpiresAt().isBefore(Instant.now())) {
            sessions.delete(session.get());
            return Optional.empty();
        }
        return users.findById(session.get().getUserId());
    }

    private String openSession(User user) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        AuthSession session = new AuthSession();
        session.setTokenHash(PasswordHasher.sha256(token));
        session.setUserId(user.getId());
        session.setExpiresAt(Instant.now().plus(SESSION_LIFETIME));
        sessions.save(session);
        sessions.deleteByExpiresAtBefore(Instant.now());
        return token;
    }

    private static String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }
}
