package com.animelist.backend.dto.assistant;

import com.animelist.backend.enums.MediaType;
import com.animelist.backend.enums.InstallmentPreference;
import com.animelist.backend.enums.ReleaseSeason;
import com.animelist.backend.enums.ReleaseStatus;
import com.animelist.backend.enums.TrackingStatus;
import java.util.List;
import jakarta.validation.constraints.Size;

public record RecommendationIntent(
        MediaType mediaType,
        @Size(max = 2) List<MediaType> excludedMediaTypes,
        @Size(max = 10) List<@Size(max = 80) String> genres,
        @Size(max = 10) List<@Size(max = 80) String> primaryGenres,
        @Size(max = 12) List<@Size(max = 80) String> tags,
        @Size(max = 10) List<@Size(max = 80) String> excludedGenres,
        @Size(max = 12) List<@Size(max = 80) String> excludedTags,
        ReleaseStatus status,
        @Size(max = 5) List<ReleaseStatus> excludedStatuses,
        @Size(max = 2) String country,
        @Size(max = 3) List<@Size(max = 2) String> excludedCountries,
        @Size(max = 20) String format,
        @Size(max = 10) List<@Size(max = 20) String> excludedFormats,
        @Size(max = 20) String sort,
        Integer yearFrom,
        Integer yearTo,
        ReleaseSeason season,
        Integer minimumUnits,
        Integer maximumUnits,
        @Size(max = 100) String referenceTitle,
        InstallmentPreference installmentPreference,
        boolean standaloneOnly,
        boolean libraryOnly,
        TrackingStatus libraryStatus
) {
    public static RecommendationIntent empty() {
        return new RecommendationIntent(null, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                null, List.of(), null, List.of(), null, List.of(), "POPULARITY", null, null, null, null, null, null,
                InstallmentPreference.FIRST, false, false, null);
    }
}
