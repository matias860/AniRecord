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

@Service
public class AniListExportService {
    private final CurrentUserService currentUser;
    private final AniListAuthService auth;
    private final LibraryEntryRepository entries;
    private final AniListExportClient client;
    private final Cache<UUID, Plan> plans = Caffeine.newBuilder().maximumSize(100).expireAfterWrite(Duration.ofHours(2)).build();
    private final Object[] writeLocks = java.util.stream.IntStream.range(0, 64).mapToObj(i -> new Object()).toArray();

    public record Change(Long entryId, int anilistId, String title, State before, State after) {}
    public record Preview(UUID id, String username, Instant expiresAt, List<Change> changes, int unchanged) {}
    private record Plan(Long userId, List<Change> changes) {}

    public AniListExportService(CurrentUserService currentUser, AniListAuthService auth, LibraryEntryRepository entries, AniListExportClient client) {
        this.currentUser = currentUser; this.auth = auth; this.entries = entries; this.client = client;
    }

    public Preview preview() {
        AppUser user = connectedUser();
        String token = token(user);
        List<LibraryEntrySummaryResponse> local = entries.findSummaries(user.getId(), MediaType.ANIME, null);
        Map<Integer, Remote> remote = client.read(token, user.getAnilistUserId());
        List<Change> changes = new ArrayList<>();
        for (var entry : local) {
            State after = desired(entry), before = remote.containsKey(entry.anilistId()) ? remote.get(entry.anilistId()).value() : null;
            if (!after.equals(before)) changes.add(new Change(entry.id(), entry.anilistId(), entry.title(), before, after));
        }
        UUID id = UUID.randomUUID();
        List<Change> immutable = List.copyOf(changes);
        plans.put(id, new Plan(user.getId(), immutable));
        return new Preview(id, user.getAnilistUsername(), Instant.now().plus(Duration.ofHours(2)), immutable, local.size() - changes.size());
    }

    public void apply(UUID planId, Long entryId) {
        AppUser user = connectedUser();
        Plan plan = plans.getIfPresent(planId);
        if (plan == null || !plan.userId().equals(user.getId())) throw new BusinessException("La vista previa venció. Revisa los cambios de nuevo.");
        synchronized (writeLocks[Math.floorMod(user.getId().hashCode(), writeLocks.length)]) {
            Change change = plan.changes().stream().filter(c -> c.entryId().equals(entryId)).findFirst()
                    .orElseThrow(() -> new BusinessException("La entrada no pertenece a esta vista previa."));
            var local = entries.findSummaries(user.getId(), MediaType.ANIME, null).stream().filter(e -> e.id().equals(entryId)).findFirst()
                    .orElseThrow(() -> new BusinessException("La biblioteca cambió. Revisa los cambios de nuevo."));
            if (!Objects.equals(local.anilistId(), change.anilistId()) || !desired(local).equals(change.after()))
                throw new BusinessException("La biblioteca cambió. Revisa los cambios de nuevo.");
            String token = token(user);
            Remote remote = client.readEntry(token, user.getAnilistUserId(), change.anilistId());
            State remoteState = remote == null ? null : remote.value();
            if (change.after().equals(remoteState)) return; // An acknowledged or uncertain earlier request already succeeded.
            if (!Objects.equals(remoteState, change.before())) throw new BusinessException("La entrada cambió en AniList. Revisa los cambios de nuevo para no sobrescribirla sin confirmar.");
            client.save(token, change.anilistId(), remote == null ? null : remote.id(), change.after());
        }
    }

    static State desired(LibraryEntrySummaryResponse entry) {
        String status = switch (entry.trackingStatus()) {
            case IN_PROGRESS -> "CURRENT";
            case COMPLETED -> "COMPLETED";
            case PLANNED -> "PLANNING";
            case PAUSED -> "PAUSED";
            case DROPPED -> "DROPPED";
        };
        return new State(status, entry.progress(), entry.personalScore() == null ? 0 : entry.personalScore().movePointRight(1).setScale(0, java.math.RoundingMode.HALF_UP).intValueExact());
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
