package com.ligalytics.betting;

import java.util.List;

/** Reglas de resolución de una combinada a partir del estado de sus selecciones. */
public final class ParlaySettlement {

    /** Resultado de la combinada y la cuota con la que se paga (producto de las selecciones ganadas). */
    public record Result(BetStatus status, double effectiveOdds) {
    }

    private ParlaySettlement() {
    }

    /**
     * Una selección perdida pierde la combinada aunque otras sigan pendientes; si alguna sigue pendiente y ninguna
     * está perdida, la combinada espera. Con todas resueltas, las anuladas cuentan con cuota 1 y, si todas se
     * anulan, se devuelve el importe.
     */
    public static Result resolve(List<BetStatus> statuses, List<Double> odds) {
        if (statuses.contains(BetStatus.LOST)) {
            return new Result(BetStatus.LOST, 0.0);
        }
        if (statuses.contains(BetStatus.PENDING)) {
            return new Result(BetStatus.PENDING, 0.0);
        }
        double effective = 1.0;
        boolean anyWon = false;
        for (int i = 0; i < statuses.size(); i++) {
            if (statuses.get(i) == BetStatus.WON) {
                effective *= odds.get(i);
                anyWon = true;
            }
        }
        return anyWon ? new Result(BetStatus.WON, effective) : new Result(BetStatus.VOID, 1.0);
    }
}
