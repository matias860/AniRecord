package com.animelist.backend.service;

import com.animelist.backend.dto.library.LibraryEntrySummaryResponse;
import com.animelist.backend.entity.AppUser;
import com.animelist.backend.enums.AccountType;
import com.animelist.backend.enums.MediaType;
import com.animelist.backend.exception.BusinessException;
import com.animelist.backend.repository.LibraryEntryRepository;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import org.springframework.stereotype.Service;
import static com.animelist.backend.service.AniListExportClient.State;
import static com.animelist.backend.service.AniListExportClient.Remote;
import static com.animelist.backend.service.AniListExportClient.Snapshot;
import static com.animelist.backend.service.AniListExportClient.ProgressUnit;

@Service
public class AniListExportService {
    private final CurrentUserService currentUser;
    private final AniListAuthService auth;
    private final LibraryEntryRepository entries;
    private final AniListExportClient client;
    private final Cache<UUID, Plan> plans = Caffeine.newBuilder().maximumSize(100).expireAfterWrite(Duration.ofHours(2)).build();
    private final Object[] writeLocks = java.util.stream.IntStream.range(0, 64).mapToObj(i -> new Object()).toArray();

    public record Change(Long entryId, int anilistId, String title, String coverImage, ProgressUnit progressUnit, State before, State after) {}
    public record Preview(UUID id, String username, MediaType mediaType, Instant expiresAt, List<Change> changes, int unchanged) {}
    private record Plan(Long userId, MediaType mediaType, String scoreFormat, List<Change> changes) {}

    public AniListExportService(CurrentUserService currentUser, AniListAuthService auth, LibraryEntryRepository entries, AniListExportClient client) {
        this.currentUser = currentUser; this.auth = auth; this.entries = entries; this.client = client;
    }

    public Preview preview() {
        return preview(MediaType.ANIME);
    }

    public Preview preview(MediaType mediaType) {
        AppUser user = connectedUser();
        String token = token(user);
        List<LibraryEntrySummaryResponse> local = entries.findSummaries(user.getId(), mediaType, null);
        Snapshot snapshot = client.read(token, user.getAnilistUserId(), mediaType);
        Map<Integer, Remote> remote = snapshot.entries();
        List<Change> changes = new ArrayList<>();
        for (var entry : local) {
            State before = remote.containsKey(entry.anilistId()) ? remote.get(entry.anilistId()).value() : null;
            State after = merge(entry, desired(entry, snapshot.scoreFormat()), before);
            if (!after.equals(before)) changes.add(new Change(entry.id(), entry.anilistId(), entry.title(), entry.coverImage(), progressUnit(entry), before, after));
        }
        UUID id = UUID.randomUUID();
        List<Change> immutable = List.copyOf(changes);
        plans.put(id, new Plan(user.getId(), mediaType, snapshot.scoreFormat(), immutable));
        return new Preview(id, user.getAnilistUsername(), mediaType, Instant.now().plus(Duration.ofHours(2)), immutable, local.size() - changes.size());
    }

    public void apply(UUID planId, Long entryId) {
        AppUser user = connectedUser();
        Plan plan = plans.getIfPresent(planId);
        if (plan == null || !plan.userId().equals(user.getId())) throw new BusinessException("La vista previa venció. Revisa los cambios de nuevo.");
        synchronized (writeLocks[Math.floorMod(user.getId().hashCode(), writeLocks.length)]) {
            Change change = plan.changes().stream().filter(c -> c.entryId().equals(entryId)).findFirst()
                    .orElseThrow(() -> new BusinessException("La entrada no pertenece a esta vista previa."));
            var local = entries.findSummaries(user.getId(), plan.mediaType(), null).stream().filter(e -> e.id().equals(entryId)).findFirst()
                    .orElseThrow(() -> new BusinessException("La biblioteca cambió. Revisa los cambios de nuevo."));
            State refreshedAfter = merge(local, desired(local, plan.scoreFormat()), change.before());
            if (!Objects.equals(local.anilistId(), change.anilistId())
                    || progressUnit(local) != change.progressUnit()
                    || !refreshedAfter.equals(change.after()))
                throw new BusinessException("La biblioteca cambió. Revisa los cambios de nuevo.");
            String token = token(user);
            Remote remote = client.readEntry(token, user.getAnilistUserId(), change.anilistId(), plan.mediaType());
            State remoteState = remote == null ? null : remote.value();
            if (change.after().equals(remoteState)) return; // An acknowledged or uncertain earlier request already succeeded.
            if (!Objects.equals(remoteState, change.before())) throw new BusinessException("La entrada cambió en AniList. Revisa los cambios de nuevo para no sobrescribirla sin confirmar.");
            client.save(token, change.anilistId(), remote == null ? null : remote.id(), change.after(), change.progressUnit());
        }
    }

    static State desired(LibraryEntrySummaryResponse entry) {
        return desired(entry, "POINT_100");
    }

    static State desired(LibraryEntrySummaryResponse entry, String scoreFormat) {
        String status = switch (entry.trackingStatus()) {
            case IN_PROGRESS -> "CURRENT";
            case COMPLETED -> "COMPLETED";
            case PLANNED -> "PLANNING";
            case PAUSED -> "PAUSED";
            case DROPPED -> "DROPPED";
        };
        int rawScore = entry.personalScore() == null ? 0 : entry.personalScore().movePointRight(1).setScale(0, java.math.RoundingMode.HALF_UP).intValueExact();
        return new State(status, entry.progress(), scoreForFormat(rawScore, scoreFormat));
    }

    /**
     * Existing AniList data is authoritative when replacing it would lose information.
     * AniRecord may advance progress, fill an empty score, and complete an active entry,
     * but it never lowers progress, replaces a score, or changes an explicit pause/drop.
     */
    static State merge(LibraryEntrySummaryResponse entry, State local, State remote) {
        if (remote == null) return local;
        int progress = Math.max(remote.progress(), local.progress());
        int score = remote.score() > 0 ? remote.score() : local.score();
        String status = mergedStatus(entry, local, remote, progress);
        return new State(status, progress, score);
    }

    private static String mergedStatus(LibraryEntrySummaryResponse entry, State local, State remote, int progress) {
        if ("COMPLETED".equals(remote.status())) return "COMPLETED";
        boolean activeRemote = "CURRENT".equals(remote.status()) || "PLANNING".equals(remote.status());
        boolean confirmedLocalCompletion = "COMPLETED".equals(local.status())
                && entry.totalUnits() != null && progress >= entry.totalUnits();
        if (activeRemote && confirmedLocalCompletion) return "COMPLETED";
        if ("PLANNING".equals(remote.status()) && progress > 0) return "CURRENT";
        return remote.status();
    }

    static int scoreForFormat(int rawScore, String scoreFormat) {
        if (rawScore == 0) return 0;
        return switch (scoreFormat) {
            case "POINT_10" -> Math.round(rawScore / 10f) * 10;
            case "POINT_5" -> Math.max(20, Math.round(rawScore / 20f) * 20);
            case "POINT_3" -> rawScore < 48 ? 35 : rawScore < 73 ? 60 : 85;
            default -> rawScore; // POINT_100 and POINT_10_DECIMAL preserve AniRecord's tenths.
        };
    }

    private static ProgressUnit progressUnit(LibraryEntrySummaryResponse entry) {
        if (entry.mediaType() == MediaType.ANIME) return ProgressUnit.EPISODES;
        return "NOVEL".equals(entry.mediaFormat()) ? ProgressUnit.VOLUMES : ProgressUnit.CHAPTERS;
    }

    private AppUser connectedUser() {
        AppUser user = currentUser.require();
        if (user.getAccountType() != AccountType.ANILIST || user.getAnilistUserId() == null)
            throw new BusinessException("Esta función requiere una cuenta de AniRecord conectada con AniList.");
        return user;
    }

    private String token(AppUser user) {
        String token = auth.validToken(user);
        if (token == null || token.isBlank()) throw new BusinessException("La autorización de AniList venció. Inicia sesión de nuevo con AniList.");
        return token;
    }
}
