package com.animelist.backend.dto.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.animelist.backend.enums.NotificationType;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class NotificationResponseTest {
    @Test
    void normalizesNullablePersistenceBoolean() {
        var response = new NotificationResponse(
                1L,
                NotificationType.AIRING_EPISODE,
                20,
                "Anime",
                null,
                "Nuevo episodio",
                2,
                (Boolean) null,
                LocalDateTime.now());

        assertThat(response.read()).isFalse();
    }
}
