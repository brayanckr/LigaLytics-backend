package com.ligalytics.betting;

/** Estado de una apuesta. */
public enum BetStatus {
    PENDING,
    WON,
    LOST,
    /** Anulada: se devuelve el importe (p. ej. si faltan datos para liquidarla). */
    VOID
}
