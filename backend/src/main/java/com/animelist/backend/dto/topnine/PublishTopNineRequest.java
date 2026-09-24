package com.animelist.backend.dto.topnine;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record PublishTopNineRequest(
        @NotNull @Size(min = 9, max = 9) List<@NotNull Long> libraryEntryIds
) {
}
