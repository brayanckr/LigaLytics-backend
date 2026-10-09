package com.ligalytics.ai;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.OptionalDouble;
import java.util.Random;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ligalytics.ai.MatchDatasetConverter.Sample;
import com.ligalytics.model.Match;
import com.ligalytics.patterns.strategy.PoissonStrategy;
import com.ligalytics.repository.MatchRepository;
import com.ligalytics.service.SeasonUtil;

import weka.classifiers.Classifier;
import weka.classifiers.Evaluation;
import weka.classifiers.functions.Logistic;
import weka.classifiers.rules.ZeroR;
import weka.classifiers.trees.REPTree;
import weka.core.Instances;

/**
 * Servicio de entrenamiento y validación del motor de IA.
 *
 * <p>Esquema de validación (según la propuesta): las temporadas anteriores a la
 * de prueba entrenan el modelo y la temporada de prueba (por defecto 2023-24)
 * mide su calidad, con <b>accuracy</b> para el resultado y <b>error absoluto
 * medio (MAE)</b> para goles, córneres y tarjetas. Cada métrica se compara con
 * un modelo trivial (clase mayoritaria / media) para saber si el modelo aporta
 * algo. Si no hay datos suficientes para separar entrenamiento y prueba se
 * usa validación cruzada. Una vez medido, los modelos que se guardan se
 * reentrenan con todos los partidos disponibles.</p>
 *
 * <ul>
 *   <li>Resultado: regresión logística (Weka {@code Logistic}).</li>
 *   <li>Goles: distribución de Poisson (modelo estadístico, sin entrenamiento).</li>
 *   <li>Córneres y tarjetas: árbol de regresión (Weka {@code REPTree}).</li>
 * </ul>
 */
@Service
public class WekaTrainingService {

    private static final Logger log = LoggerFactory.getLogger(WekaTrainingService.class);

    private final MatchRepository matchRepository;
    private final MatchDatasetConverter datasetConverter;
    private final WekaModelManager modelManager;
    private final TrainingReportHolder reportHolder;
    private final AiProperties properties;

    public WekaTrainingService(MatchRepository matchRepository,
            MatchDatasetConverter datasetConverter,
            WekaModelManager modelManager,
            TrainingReportHolder reportHolder,
            AiProperties properties) {
        this.matchRepository = matchRepository;
        this.datasetConverter = datasetConverter;
        this.modelManager = modelManager;
        this.reportHolder = reportHolder;
        this.properties = properties;
    }

    /** Entrena y valida todos los modelos a partir de los datos de la BD. */
    @Transactional(readOnly = true)
    public TrainingReport trainAll() {
        return trainAll(null);
    }

    /**
     * Igual que {@link #trainAll()} pero solo usa partidos desde la temporada indicada
     * (año de inicio, p. ej. 2022 = 2022/23). El historial anterior sigue alimentando
     * las variables (Elo, forma...), solo deja de usarse para entrenar y validar.
     */
    @Transactional(readOnly = true)
    public TrainingReport trainAll(Integer fromSeasonStartYear) {
        int from = fromSeasonStartYear != null ? fromSeasonStartYear : properties.getTrainFromSeasonStartYear();
        return train(matchRepository.findAllWithTeams(), from);
    }

    /** Entrena y valida todos los modelos a partir de una lista de partidos. */
    public TrainingReport train(List<Match> matches) {
        return train(matches, properties.getTrainFromSeasonStartYear());
    }

    public synchronized TrainingReport train(List<Match> matches, int fromSeasonStartYear) {
        List<Sample> samples = datasetConverter.samples(matches).stream()
                .filter(sample -> sample.seasonStartYear() >= fromSeasonStartYear)
                .toList();
        Split split = split(samples);

        List<TrainingReport.TargetResult> results = new ArrayList<>();
        for (PredictionTarget target : PredictionTarget.values()) {
            results.add(trainTarget(target, samples, split));
        }
        TrainingReport report = new TrainingReport(Instant.now(), samples.size(), split.description(), results);
        reportHolder.update(report);
        log.info("Entrenamiento completado ({}): {} modelos operativos de {} objetivos", split.description(),
                report.trainedModels(), PredictionTarget.values().length);
        return report;
    }

    private Split split(List<Sample> samples) {
        if (samples.isEmpty()) {
            return new Split(List.of(), List.of(), false, "Sin datos de partidos");
        }
        int testYear = properties.getTestSeasonStartYear() > 0
                ? properties.getTestSeasonStartYear()
                : samples.stream().mapToInt(Sample::seasonStartYear).max().orElse(0);

        List<Sample> train = samples.stream().filter(s -> s.seasonStartYear() < testYear).toList();
        List<Sample> test = samples.stream().filter(s -> s.seasonStartYear() == testYear).toList();

        int minimum = properties.getMinTrainingMatches();
        if (train.size() >= minimum && test.size() >= minimum) {
            int firstYear = train.stream().mapToInt(Sample::seasonStartYear).min().orElse(testYear);
            int lastYear = train.stream().mapToInt(Sample::seasonStartYear).max().orElse(testYear);
            String description = "Entrenamiento " + SeasonUtil.label(firstYear) + " a " + SeasonUtil.label(lastYear)
                    + " (" + train.size() + " partidos) · Prueba " + SeasonUtil.label(testYear)
                    + " (" + test.size() + " partidos)";
            return new Split(train, test, true, description);
        }
        return new Split(samples, List.of(), false, "Validación cruzada de "
                + Math.min(properties.getCrossValidationFolds(), Math.max(samples.size(), 2)) + " particiones sobre "
                + samples.size() + " partidos (no hay datos suficientes para separar entrenamiento y prueba)");
    }

    private TrainingReport.TargetResult trainTarget(PredictionTarget target, List<Sample> samples, Split split) {
        try {
            return target == PredictionTarget.GOLES
                    ? evaluatePoisson(samples, split)
                    : evaluateAndTrainWeka(target, samples, split);
        } catch (Exception ex) {
            log.error("[{}] Error durante el entrenamiento", target.code(), ex);
            return failed(target, algorithmName(target), "Error: " + ex.getMessage());
        }
    }

    private TrainingReport.TargetResult evaluateAndTrainWeka(PredictionTarget target,
            List<Sample> samples,
            Split split) throws Exception {
        Instances all = datasetConverter.build(target, samples);
        if (all.numInstances() < properties.getMinTrainingMatches()) {
            String message = "Datos insuficientes: " + all.numInstances() + " instancias (mínimo "
                    + properties.getMinTrainingMatches() + ")";
            log.warn("[{}] {}", target.code(), message);
            return failed(target, algorithmName(target), message);
        }
        if (!target.isNumeric() && all.numDistinctValues(all.classIndex()) < 2) {
            return failed(target, algorithmName(target), "El dataset contiene una única clase");
        }

        double metric;
        double baseline;
        int trainCount;
        int testCount;
        Instances train = split.holdout() ? datasetConverter.build(target, split.train()) : all;
        Instances test = split.holdout() ? datasetConverter.build(target, split.test()) : null;

        if (test != null && train.numInstances() >= 2 && test.numInstances() > 0) {
            Classifier model = createClassifier(target);
            model.buildClassifier(train);
            Evaluation evaluation = new Evaluation(train);
            evaluation.evaluateModel(model, test);

            ZeroR trivial = new ZeroR();
            trivial.buildClassifier(train);
            Evaluation trivialEvaluation = new Evaluation(train);
            trivialEvaluation.evaluateModel(trivial, test);

            metric = metricOf(target, evaluation);
            baseline = metricOf(target, trivialEvaluation);
            trainCount = train.numInstances();
            testCount = test.numInstances();
        } else {
            int folds = Math.max(2, Math.min(properties.getCrossValidationFolds(), all.numInstances()));
            Evaluation evaluation = new Evaluation(all);
            evaluation.crossValidateModel(createClassifier(target), all, folds, new Random(properties.getRandomSeed()));
            Evaluation trivialEvaluation = new Evaluation(all);
            trivialEvaluation.crossValidateModel(new ZeroR(), all, folds, new Random(properties.getRandomSeed()));

            metric = metricOf(target, evaluation);
            baseline = metricOf(target, trivialEvaluation);
            trainCount = all.numInstances();
            testCount = all.numInstances();
        }

        // Modelo definitivo: reentrenado con todos los partidos disponibles.
        Classifier finalModel = createClassifier(target);
        finalModel.buildClassifier(all);
        modelManager.save(target, finalModel);

        return new TrainingReport.TargetResult(target.code(), algorithmName(target), true, trainCount, testCount,
                metricName(target), round(metric), round(baseline), modelManager.modelFile(target).toString(),
                "Modelo entrenado correctamente");
    }

    /** Poisson no se entrena: solo se mide su error sobre el conjunto de prueba. */
    private TrainingReport.TargetResult evaluatePoisson(List<Sample> samples, Split split) {
        List<Sample> evaluationSet = split.holdout() ? split.test() : samples;
        List<Sample> baselineSource = split.holdout() ? split.train() : samples;

        double baselineMean = baselineSource.stream()
                .map(s -> PredictionTarget.GOLES.numericValue(s.match()))
                .filter(OptionalDouble::isPresent)
                .mapToDouble(OptionalDouble::getAsDouble)
                .average()
                .orElse(2.6);

        double errorSum = 0.0;
        double baselineErrorSum = 0.0;
        int count = 0;
        for (Sample sample : evaluationSet) {
            OptionalDouble actual = PredictionTarget.GOLES.numericValue(sample.match());
            if (actual.isEmpty()) {
                continue;
            }
            double[] lambdas = PoissonStrategy.lambdas(sample.analysis());
            errorSum += Math.abs(lambdas[0] + lambdas[1] - actual.getAsDouble());
            baselineErrorSum += Math.abs(baselineMean - actual.getAsDouble());
            count++;
        }
        if (count == 0) {
            return failed(PredictionTarget.GOLES, algorithmName(PredictionTarget.GOLES), "Sin partidos para evaluar");
        }
        return new TrainingReport.TargetResult(PredictionTarget.GOLES.code(),
                algorithmName(PredictionTarget.GOLES), true,
                split.holdout() ? split.train().size() : samples.size(), count, "MAE",
                round(errorSum / count), round(baselineErrorSum / count), null,
                "Modelo estadístico (sin entrenamiento): λ de Poisson por equipo");
    }

    private Classifier createClassifier(PredictionTarget target) {
        if (!target.isNumeric()) {
            return new Logistic();
        }
        REPTree tree = new REPTree();
        tree.setSeed((int) properties.getRandomSeed());
        tree.setMinNum(5.0);
        return tree;
    }

    private static String algorithmName(PredictionTarget target) {
        return switch (target) {
            case RESULTADO -> "Regresión logística (Weka Logistic)";
            case GOLES -> "Distribución de Poisson";
            case CORNERES, TARJETAS -> "Árbol de regresión (Weka REPTree)";
        };
    }

    private static String metricName(PredictionTarget target) {
        return target.isNumeric() ? "MAE" : "accuracy";
    }

    private static double metricOf(PredictionTarget target, Evaluation evaluation) {
        return target.isNumeric() ? evaluation.meanAbsoluteError() : evaluation.pctCorrect() / 100.0;
    }

    private static double round(double value) {
        return Math.round(value * 10_000.0) / 10_000.0;
    }

    private static TrainingReport.TargetResult failed(PredictionTarget target, String algorithm, String message) {
        return new TrainingReport.TargetResult(target.code(), algorithm, false, 0, 0, metricName(target), null, null,
                null, message);
    }

    private record Split(List<Sample> train, List<Sample> test, boolean holdout, String description) {
        Split {
            train = train.stream().sorted(Comparator.comparing(s -> s.match().getMatchDate())).toList();
        }
    }
}
