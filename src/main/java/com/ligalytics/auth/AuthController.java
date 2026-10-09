package com.ligalytics.auth;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Cuentas de la demostración (saldo ficticio). */
@RestController
@RequestMapping(value = "/auth", produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Cuentas", description = "Registro e inicio de sesión de la demostración de apuestas")
public class AuthController {

    public record RegisterRequest(@NotBlank String email, @NotBlank @Size(max = 40) String displayName,
            @NotBlank @Size(max = 100) String password) {
    }

    public record LoginRequest(@NotBlank String email, @NotBlank String password) {
    }

    public record AccountDto(Long id, String email, String displayName, long balance) {
        static AccountDto of(User user) {
            return new AccountDto(user.getId(), user.getEmail(), user.getDisplayName(), user.getBalance());
        }
    }

    public record AuthResponse(String token, AccountDto account) {
    }

    private final AuthService authService;
    private final UserRepository users;

    public AuthController(AuthService authService, UserRepository users) {
        this.authService = authService;
        this.users = users;
    }

    @PostMapping("/register")
    @Operation(summary = "Crea una cuenta con 100 000 COP ficticios")
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        AuthService.AuthResult result = authService.register(request.email(), request.displayName(), request.password());
        return new AuthResponse(result.token(), AccountDto.of(result.user()));
    }

    @PostMapping("/login")
    @Operation(summary = "Inicia sesión")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        AuthService.AuthResult result = authService.login(request.email(), request.password());
        return new AuthResponse(result.token(), AccountDto.of(result.user()));
    }

    @PostMapping("/logout")
    @Operation(summary = "Cierra la sesión actual")
    public void logout(@RequestAttribute(AuthInterceptor.TOKEN_ATTRIBUTE) String token) {
        authService.logout(token);
    }

    @GetMapping("/me")
    @Operation(summary = "Cuenta de la sesión actual, con el saldo ficticio")
    public AccountDto me(@RequestAttribute(AuthInterceptor.USER_ATTRIBUTE) User user) {
        return AccountDto.of(users.findById(user.getId()).orElse(user));
    }
}
