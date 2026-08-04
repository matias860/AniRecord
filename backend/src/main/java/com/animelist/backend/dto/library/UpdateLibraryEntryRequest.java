package com.animelist.backend.dto.library;

import com.animelist.backend.enums.TrackingStatus;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.time.LocalDate;

public record UpdateLibraryEntryRequest(
        @PositiveOrZero Integer progress,
        @DecimalMin("0.0") @DecimalMax("10.0") BigDecimal personalScore,
        TrackingStatus trackingStatus,
        LocalDate startedAt,
        LocalDate completedAt,
        Boolean clearScore
) {
}
