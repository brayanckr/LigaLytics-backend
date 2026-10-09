package com.ligalytics.auth;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AuthSessionRepository extends JpaRepository<AuthSession, Long> {

    Optional<AuthSession> findByTokenHash(String tokenHash);

    void deleteByExpiresAtBefore(Instant moment);

    void deleteByTokenHash(String tokenHash);
}
