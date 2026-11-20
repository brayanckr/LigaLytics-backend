package com.ligalytics.betting;

import java.util.function.BiPredicate;

/**
 * Mercados que se deciden solo con el marcador final (ganador, goles totales y ambos marcan), expresados como
 * condiciones sobre {@code (goles local, goles visitante)}. Con ellas se razona sobre dos selecciones del mismo partido
 * igual que las casas de apuestas en «crear apuesta»: se pueden combinar mientras no se contradigan ni una implique a
 * la otra, y entonces su cuota conjunta no es el producto de las dos (ver {@link MarketModel#correlationFactor}).
 */
public final class ScoreMarkets {

    /** Marcadores comprobados: de 0 a 10 goles por equipo, el mismo rango que la matriz del modelo. */
    static final int MAX_GOALS = 10;

    private ScoreMarkets() {
    }

    /** Condición de la selección sobre el marcador, o nulo si el mercado no depende solo del marcador (córneres, tarjetas). */
    public static BiPredicate<Integer, Integer> condition(Market market, String selection, Double line) {
        String pick = selection == null ? "" : selection.trim().toUpperCase();
        return switch (market) {
            case WINNER -> switch (pick) {
                case "HOME" -> (h, a) -> h > a;
                case "DRAW" -> (h, a) -> h.equals(a);
                case "AWAY" -> (h, a) -> h < a;
                default -> null;
            };
            case GOALS -> line == null ? null : switch (pick) {
                case "OVER" -> (h, a) -> h + a > line;
                case "UNDER" -> (h, a) -> h + a < line;
                default -> null;
            };
            case BTTS -> switch (pick) {
                case "YES" -> (h, a) -> h > 0 && a > 0;
                case "NO" -> (h, a) -> h == 0 || a == 0;
                default -> null;
            };
            default -> null;
        };
    }

    /**
     * Si dos selecciones del mismo partido no se pueden combinar. Es así cuando son del mismo mercado (dos resultados
     * 1X2, más y menos de goles, ambos marcan sí y no, o la misma repetida), cuando no pueden cumplirse a la vez
     * (ambos marcan «sí» con menos de 1,5 goles) o cuando una implica a la otra (ambos marcan «sí» con más de 1,5
     * goles): en ese caso multiplicar las cuotas inflaría el premio sin riesgo añadido.
     */
    public static boolean conflicts(Market marketA, String selectionA, Double lineA,
            Market marketB, String selectionB, Double lineB) {
        if (marketA == marketB) {
            return true;
        }
        BiPredicate<Integer, Integer> a = condition(marketA, selectionA, lineA);
        BiPredicate<Integer, Integer> b = condition(marketB, selectionB, lineB);
        if (a == null || b == null) {
            return false; // córneres y tarjetas no se determinan por el marcador: se combinan con cualquier otro mercado
        }
        boolean both = false;
        boolean onlyA = false;
        boolean onlyB = false;
        for (int home = 0; home <= MAX_GOALS; home++) {
            for (int away = 0; away <= MAX_GOALS; away++) {
                boolean inA = a.test(home, away);
                boolean inB = b.test(home, away);
                both |= inA && inB;
                onlyA |= inA && !inB;
                onlyB |= !inA && inB;
            }
        }
        return !both || !onlyA || !onlyB;
    }
}
