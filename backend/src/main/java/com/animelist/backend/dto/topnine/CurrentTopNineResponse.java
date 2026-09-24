package com.animelist.backend.dto.topnine;

import java.util.List;

public record CurrentTopNineResponse(
        TopNinePromptResponse prompt,
        TopNineSubmissionResponse mine,
        List<TopNineSubmissionResponse> submissions
) {
}
