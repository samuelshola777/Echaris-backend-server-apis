package com.echaris.api.commitmentduration;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface CommitmentDurationRepository
        extends MongoRepository<CommitmentDuration, String> {

    List<CommitmentDuration> findAllByActiveTrueOrderByDurationMonthsAsc();

    List<CommitmentDuration> findAllByOrderByDurationMonthsAsc();

    boolean existsByDurationMonths(int durationMonths);

    boolean existsByDurationMonthsAndIdNot(
            int durationMonths,
            String id
    );

    boolean existsByDurationMonthsAndActiveTrue(int durationMonths);
}