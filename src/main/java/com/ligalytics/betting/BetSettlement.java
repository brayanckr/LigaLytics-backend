package com.ligalytics.betting;

import java.util.Optional;

/** Reglas de liquidación de cada mercado a partir del resultado real del partido. */
public final class BetSettlement {

    private BetSettlement() {
    }

    /**
     * Resultado de una apuesta con los datos reales del partido.
     *
     * @param corners córneres totales (puede ser nulo si no se conocen)
     * @param cards   tarjetas totales, amarillas + rojas (puede ser nulo si no se conocen)
     * @return {@code WON} o {@code LOST}; vacío si faltan datos para decidirla
     */
    public static Optional<BetStatus> outcome(Market market, String selection, Double line,
            int homeGoals, int awayGoals, Integer corners, Integer cards) {
        Boolean won = switch (market) {
            case WINNER -> switch (selection.toUpperCase()) {
                case "HOME" -> homeGoals > awayGoals;
                case "DRAW" -> homeGoals == awayGoals;
                case "AWAY" -> homeGoals < awayGoals;
                default -> null;
            };
            case GOALS -> overUnder(selection, line, homeGoals + awayGoals);
            case BTTS -> switch (selection.toUpperCase()) {
                case "YES" -> homeGoals > 0 && awayGoals > 0;
                case "NO" -> homeGoals == 0 || awayGoals == 0;
                default -> null;
            };
            case CORNERS -> corners == null ? null : overUnder(selection, line, corners);
            case CARDS -> cards == null ? null : overUnder(selection, line, cards);
        };
        return won == null ? Optional.empty() : Optional.of(won ? BetStatus.WON : BetStatus.LOST);
    }

    private static Boolean overUnder(String selection, Double line, int total) {
        if (line == null) {
            return null;
        }
        return switch (selection.toUpperCase()) {
            case "OVER" -> total > line;
            case "UNDER" -> total < line;
            default -> null;
        };
    }
}
