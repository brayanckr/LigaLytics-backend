package com.ligalytics.betting;

/**
 * Una cuota ofrecida: mercado, selección, línea (si aplica) y origen.
 *
 * @param source {@code consenso} (cuota media de casas de apuestas reales) o {@code demo} (calculada por el modelo)
 */
public record OddsLine(Market market, String selection, Double line, double odds, String source) {

    public boolean sameAs(Market otherMarket, String otherSelection, Double otherLine) {
        boolean sameLine = line == null ? otherLine == null : otherLine != null && Math.abs(line - otherLine) < 1e-9;
        return market == otherMarket && selection.equalsIgnoreCase(otherSelection) && sameLine;
    }
}
