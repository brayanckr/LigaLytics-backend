package com.ligalytics.patterns.observer;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

class ETLSubjectTest {

    private final EtlEvent event = EtlEvent.of(
            "football-data", "E0-2024", "2024", Set.of(1L, 2L), 3, 0, 1, "https://example.com");

    @Test
    void notifiesAllRegisteredObservers() {
        ETLObserver first = mock(ETLObserver.class);
        ETLObserver second = mock(ETLObserver.class);
        ETLSubject subject = new ETLSubject(List.of(first, second));

        subject.notifyObservers(event);

        verify(first).onEtlCompleted(event);
        verify(second).onEtlCompleted(event);
        assertEquals(2, subject.observers().size());
    }

    @Test
    void stopsNotifyingUnregisteredObservers() {
        ETLObserver first = mock(ETLObserver.class);
        ETLObserver second = mock(ETLObserver.class);
        ETLSubject subject = new ETLSubject(List.of(first, second));

        assertTrue(subject.unregister(second));
        subject.notifyObservers(event);

        verify(first).onEtlCompleted(event);
        verify(second, never()).onEtlCompleted(event);
        assertEquals(1, subject.observers().size());
    }

    @Test
    void isolatesObserverFailures() {
        ETLObserver failing = mock(ETLObserver.class);
        when(failing.name()).thenReturn("failing");
        doThrow(new RuntimeException("boom")).when(failing).onEtlCompleted(event);
        ETLObserver healthy = mock(ETLObserver.class);
        ETLSubject subject = new ETLSubject(List.of(failing, healthy));

        assertDoesNotThrow(() -> subject.notifyObservers(event));

        verify(healthy).onEtlCompleted(event);
    }

    @Test
    void supportsDynamicRegistration() {
        ETLSubject subject = new ETLSubject(List.of());
        ETLObserver observer = mock(ETLObserver.class);

        subject.register(observer);
        subject.register(observer);
        subject.notifyObservers(event);

        verify(observer).onEtlCompleted(event);
        assertEquals(1, subject.observers().size());
    }

    @Test
    void ignoresNullEvents() {
        ETLObserver observer = mock(ETLObserver.class);
        ETLSubject subject = new ETLSubject(List.of(observer));

        subject.notifyObservers(null);

        verify(observer, never()).onEtlCompleted(null);
    }
}
