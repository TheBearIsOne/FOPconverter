package com.example.fopconverter.application.port.out;

import com.example.fopconverter.domain.model.ConversionJob;
import com.example.fopconverter.domain.model.JobStatusSnapshot;
import java.util.Optional;
import java.util.UUID;

public interface JobStatusStorePort {
    void save(ConversionJob job);

    Optional<JobStatusSnapshot> findById(UUID jobId);
}