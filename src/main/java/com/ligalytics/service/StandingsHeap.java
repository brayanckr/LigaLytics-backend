package com.ligalytics.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * Cola de prioridad (heap binario) para la clasificación de la liga.
 *
 * <p>Se usa un {@link PriorityQueue} (min-heap con el comparador invertido: el
 * mejor equipo queda en la raíz). Construir el heap cuesta O(n) y cada
 * extracción O(log n), de modo que obtener el top-k cuesta O(n + k·log n) sin
 * ordenar toda la colección.</p>
 *
 * <p>Criterios de desempate: puntos, diferencia de goles, goles a favor y, por
 * último, nombre (orden alfabético).</p>
 */
public final class StandingsHeap {

    /** Fila mínima necesaria para ordenar la tabla. */
    public record Standing(Long teamId, String teamName, int points, int goalDifference, int goalsFor) {
    }

    private static final Comparator<Standing> BEST_FIRST = Comparator
            .comparingInt(Standing::points).reversed()
            .thenComparing(Comparator.comparingInt(Standing::goalDifference).reversed())
            .thenComparing(Comparator.comparingInt(Standing::goalsFor).reversed())
            .thenComparing(Standing::teamName, String.CASE_INSENSITIVE_ORDER);

    private final PriorityQueue<Standing> heap;

    public StandingsHeap(Collection<Standing> standings) {
        this.heap = new PriorityQueue<>(Math.max(1, standings.size()), BEST_FIRST);
        this.heap.addAll(standings);
    }

    public int size() {
        return heap.size();
    }

    /** Extrae los {@code k} mejores equipos en orden (consume el heap). */
    public List<Standing> pollTop(int k) {
        List<Standing> top = new ArrayList<>(Math.min(k, heap.size()));
        while (top.size() < k && !heap.isEmpty()) {
            top.add(heap.poll());
        }
        return top;
    }

    /** Extrae la tabla completa en orden (consume el heap). */
    public List<Standing> pollAll() {
        return pollTop(heap.size());
    }

    /** Tabla ordenada de mejor a peor. */
    public static List<Standing> rank(Collection<Standing> standings) {
        return new StandingsHeap(standings).pollAll();
    }

    /** Posición (1 = líder) de cada equipo. */
    public static Map<Long, Integer> positions(Collection<Standing> standings) {
        Map<Long, Integer> positions = new HashMap<>();
        int position = 1;
        for (Standing standing : rank(standings)) {
            positions.put(standing.teamId(), position++);
        }
        return positions;
    }
}
