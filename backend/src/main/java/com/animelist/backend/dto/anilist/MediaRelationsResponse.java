package com.animelist.backend.dto.anilist;

import java.util.List;

public record MediaRelationsResponse(
        Integer sourceAnilistId,
        List<MediaRelationResponse> relations
) {
}
