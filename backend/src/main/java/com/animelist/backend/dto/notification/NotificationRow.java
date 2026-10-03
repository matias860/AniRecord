package com.animelist.backend.dto.notification;

import java.time.LocalDateTime;

/** Database projection kept separate from the public notification contract. */
public interface NotificationRow {
    Long getId();
    String getType();
    Integer getAnilistMediaId();
    String getTitle();
    String getCoverImage();
    String getMessage();
    Integer getEpisode();
    Boolean getRead();
    LocalDateTime getOccurredAt();
}
