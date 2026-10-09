package com.ligalytics.ai;

import java.util.Comparator;
import java.util.List;
import java.util.OptionalDouble;

import org.springframework.stereotype.Component;

import com.ligalytics.model.Match;
import com.ligalytics.patterns.builder.MatchAnalysis;
import com.ligalytics.service.SeasonUtil;

import weka.core.DenseInstance;
import weka.core.Instances;

/**
 * Conversor de partidos históricos ({@link Match}) a instancias de Weka.
 *
 * <p>Para cada partido reconstruye, con {@link TeamFormTracker}, la información
 * disponible <i>antes</i> de jugarse (patrón Builder → {@link MatchAnalysis}) y
 * la combina con el resultado real como etiqueta. Así el entrenamiento nunca
 * ve datos del futuro.</p>
 */
@Component
public class MatchDatasetConverter {

    /**
     * Partido histórico con las variables conocidas antes de jugarse.
     *
     * @param match           partido real (con su resultado)
     * @param analysis        variables previas al partido
     * @param seasonStartYear año de inicio de su temporada (2023 = 2023-24)
     */
    public record Sample(Match match, MatchAnalysis analysis, int seasonStartYear) {
    }

    /**
     * Reproduce la liga en orden cronológico y devuelve una muestra por cada
     * partido con marcador.
     */
    public List<Sample> samples(List<Match> matches) {
        TeamFormTracker tracker = new TeamFormTracker();
        List<Sample> samples = new java.util.ArrayList<>();
        if (matches == null) {
            return samples;
        }
        List<Match> ordered = matches.stream()
                .filter(match -> match.getMatchDate() != null && match.getHomeTeam() != null
                        && match.getAwayTeam() != null)
                .sorted(Comparator.comparing(Match::getMatchDate))
                .toList();

        for (Match match : ordered) {
            if (match.getFullTimeHomeGoals() == null || match.getFullTimeAwayGoals() == null) {
                continue;
            }
            MatchAnalysis analysis = tracker.analysisFor(match.getHomeTeam(), match.getAwayTeam(),
                    match.getMatchDate());
            samples.add(new Sample(match, analysis, SeasonUtil.startYear(match.getMatchDate())));
            tracker.record(match);
        }
        return samples;
    }

    /**
     * Reproduce todos los partidos y devuelve el tracker en su estado final,
     * listo para describir un partido futuro.
     */
    public TeamFormTracker replay(List<Match> matches) {
        TeamFormTracker tracker = new TeamFormTracker();
        if (matches != null) {
            matches.stream()
                    .filter(match -> match.getMatchDate() != null)
                    .sorted(Comparator.comparing(Match::getMatchDate))
                    .forEach(tracker::record);
        }
        return tracker;
    }

    /**
     * Construye el dataset de Weka de un objetivo con las muestras indicadas
     * (se omiten las que no tienen etiqueta para ese objetivo).
     */
    public Instances build(PredictionTarget target, List<Sample> samples) {
        Instances dataset = MatchFeatureExtractor.createDataset(target);
        for (Sample sample : samples) {
            double label;
            if (target.isNumeric()) {
                OptionalDouble value = target.numericValue(sample.match());
                if (value.isEmpty()) {
                    continue;
                }
                label = value.getAsDouble();
            } else {
                String name = target.labelFor(sample.match());
                if (name == null) {
                    continue;
                }
                label = dataset.classAttribute().indexOfValue(name);
            }

            double[] features = MatchFeatureExtractor.features(sample.analysis());
            double[] values = new double[dataset.numAttributes()];
            System.arraycopy(features, 0, values, 0, features.length);
            values[dataset.classIndex()] = label;

            DenseInstance instance = new DenseInstance(1.0, values);
            instance.setDataset(dataset);
            dataset.add(instance);
        }
        return dataset;
    }

    /** Atajo: dataset de un objetivo directamente desde los partidos. */
    public Instances buildFromMatches(PredictionTarget target, List<Match> matches) {
        return build(target, samples(matches));
    }

    /** Cabecera vacía del dataset (sin instancias) para un objetivo. */
    public Instances header(PredictionTarget target) {
        return MatchFeatureExtractor.createDataset(target);
    }
}
