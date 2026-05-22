package com.example.fopconverter.application.port.in;

import com.example.fopconverter.application.dto.SyncConversionResponse;
import java.util.UUID;

public interface DownloadArtifactUseCase {
    SyncConversionResponse download(UUID jobId);
}