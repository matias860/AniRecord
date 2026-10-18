package com.animelist.backend.dto.assistant;

import com.animelist.backend.enums.MediaType;
import com.animelist.backend.enums.ReleaseStatus;
import com.animelist.backend.enums.TrackingStatus;
import java.util.List;

public record AssistantMediaResponse(
        Integer anilistId,
        MediaType mediaType,
        String mediaFormat,
        String title,
        String romajiTitle,
        String coverImage,
        Integer totalUnits,
        ReleaseStatus releaseStatus,
        Integer releaseYear,
        List<String> genres,
        List<String> tags,
        TrackingStatus libraryStatus,
        String reason
) {}
