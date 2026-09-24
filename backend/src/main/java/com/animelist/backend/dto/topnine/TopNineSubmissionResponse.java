package com.animelist.backend.dto.topnine;

import java.time.LocalDateTime;
import java.util.List;

public record TopNineSubmissionResponse(
        Long id,
        Long userId,
        String userName,
        String userAvatar,
        LocalDateTime publishedAt,
        List<TopNineMediaResponse> selections
) {
}
