package com.ligalytics.service;

import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.ligalytics.model.PredictionRecord;
import com.ligalytics.repository.PredictionRecordRepository;
import com.ligalytics.service.dto.PredictionResponseDto;

/**
 * Guarda en PostgreSQL cada predicción generada. Usa su propia transacción para
 * que un fallo al registrar nunca afecte a la respuesta que ve el usuario.
 */
@Service
public class PredictionLogService {

    private static final Logger log = LoggerFactory.getLogger(PredictionLogService.class);

    private final PredictionRecordRepository repository;

    public PredictionLogService(PredictionRecordRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void save(PredictionResponseDto prediction) {
        try {
            repository.save(PredictionRecord.builder()
                    .createdAt(Instant.now())
                    .homeTeamId(prediction.homeTeamId())
                    .awayTeamId(prediction.awayTeamId())
                    .homeTeam(prediction.homeTeam())
                    .awayTeam(prediction.awayTeam())
                    .predictedOutcome(prediction.predictedOutcome())
                    .homeWinProbability(prediction.homeWinProbability())
                    .drawProbability(prediction.drawProbability())
                    .awayWinProbability(prediction.awayWinProbability())
                    .expectedHomeGoals(prediction.expectedHomeGoals())
                    .expectedAwayGoals(prediction.expectedAwayGoals())
                    .expectedCorners(prediction.expectedCorners())
                    .expectedCards(prediction.expectedCards())
                    .winnerSource(prediction.winnerSource())
                    .ownHomeWin(prediction.ownHomeWinProbability())
                    .ownDraw(prediction.ownDrawProbability())
                    .ownAwayWin(prediction.ownAwayWinProbability())
                    .build());
        } catch (RuntimeException ex) {
            log.warn("No se pudo guardar la predicción {} vs {}: {}", prediction.homeTeam(), prediction.awayTeam(),
                    ex.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public List<PredictionRecord> latest() {
        return repository.findTop20ByOrderByCreatedAtDesc();
    }
}
