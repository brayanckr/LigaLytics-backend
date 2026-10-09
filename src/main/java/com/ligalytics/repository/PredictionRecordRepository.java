package com.ligalytics.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.ligalytics.model.PredictionRecord;

@Repository
public interface PredictionRecordRepository extends JpaRepository<PredictionRecord, Long> {

    List<PredictionRecord> findTop20ByOrderByCreatedAtDesc();
}
