package com.animelist.backend.dto.library;

import com.animelist.backend.enums.TrackingStatus;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record AddLibraryEntryRequest(
        @NotNull @Positive Integer anilistId,
        TrackingStatus trackingStatus
) {
}
