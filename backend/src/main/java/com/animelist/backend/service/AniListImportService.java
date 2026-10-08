package com.animelist.backend.service;

import com.animelist.backend.dto.anilist.AniListLibraryExport;
import com.animelist.backend.dto.anilist.AniListLibraryItem;
import com.animelist.backend.dto.anilist.AniListPersonFavorite;
import com.animelist.backend.dto.library.LibraryEntrySummaryResponse;
import com.animelist.backend.entity.AppUser;
import com.animelist.backend.enums.AccountType;
import com.animelist.backend.enums.MediaType;
import com.animelist.backend.enums.TrackingStatus;
import com.animelist.backend.exception.BusinessException;
import com.animelist.backend.repository.LibraryEntryRepository;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

@Service
public class AniListImportService {
    public static final int BATCH_SIZE = 20;

    public record LocalState(Integer progress, BigDecimal score, Integer repeats,
            com.animelist.backend.enums.TrackingStatus status, LocalDate startedAt, LocalDate completedAt) {}
    public record Change(int anilistId, String title, String coverImage, MediaType mediaType,
            String mediaFormat, boolean addition, LocalState before, LocalState after) {}
    public record Preview(UUID id, String username, Instant expiresAt, int batchSize,
            int unchanged, List<Change> changes) {}
    public record BatchResult(int processed, int created, int updated) {}
    public record CompleteResult(int personFavoritesCreated, int characterFavoritesCreated,
            int staffFavoritesCreated) {}

    private static final class Plan {
        private final Long userId;
        private final AniListLibraryExport library;
        private final List<AniListPersonFavorite> favorites;
        private final List<AniListLibraryItem> entries;
        private final Map<Integer, AniListLibraryItem> entriesById;
        private final Map<Integer, LocalState> before;
        private final Map<String, BatchResult> completedSelections = new ConcurrentHashMap<>();
        private final java.util.Set<Integer> appliedIds = ConcurrentHashMap.newKeySet();

        private Plan(Long userId, AniListLibraryExport library, List<AniListPersonFavorite> favorites,
                List<AniListLibraryItem> entries, Map<Integer, LocalState> before) {
            this.userId = userId;
            this.library = library;
            this.favorites = favorites;
            this.entries = entries;
            this.entriesById = entries.stream().collect(java.util.stream.Collectors.toUnmodifiableMap(
                    item -> item.media().anilistId(), item -> item));
            this.before = before;
        }
    }

    private final CurrentUserService currentUser;
    private final AniListAuthService auth;
    private final AniListClient client;
    private final LibraryEntryRepository entries;
    private final LibraryImportWriter writer;
    private final Cache<UUID, Plan> plans = Caffeine.newBuilder()
            .maximumSize(4)
            .expireAfterWrite(Duration.ofMinutes(30))
            .build();
    private final Object[] writeLocks = java.util.stream.IntStream.range(0, 64).mapToObj(i -> new Object()).toArray();

    public AniListImportService(CurrentUserService currentUser, AniListAuthService auth,
            AniListClient client, LibraryEntryRepository entries, LibraryImportWriter writer) {
        this.currentUser = currentUser;
        this.auth = auth;
        this.client = client;
        this.entries = entries;
        this.writer = writer;
    }

    public Preview preview() {
        AppUser user = connectedUser();
        String token = token(user);
        AniListLibraryExport external = client.getUserLibrary(user.getAnilistUsername().trim(), token);
        List<AniListPersonFavorite> favorites = client.getUserPersonFavorites(user.getAnilistUsername().trim(), token);
        Map<Integer, LocalState> before = new HashMap<>();
        for (LibraryEntrySummaryResponse entry : entries.findSummaries(user.getId(), null, null)) {
            before.put(entry.anilistId(), state(entry));
        }

        List<AniListLibraryItem> changedItems = new ArrayList<>();
        List<Change> changes = new ArrayList<>();
        int unchanged = 0;
        for (AniListLibraryItem item : external.entries()) {
            var media = item.media();
            LocalState local = before.get(media.anilistId());
            LocalState remote = state(item);
            LocalState after = merge(local, remote, media.totalUnits());
            if (local != null && sameState(local, after)) {
                unchanged++;
                continue;
            }
            AniListLibraryItem safeItem = withState(item, after);
            changedItems.add(safeItem);
            changes.add(new Change(media.anilistId(), media.title(), media.coverImage(), media.mediaType(),
                    media.mediaFormat(), local == null, local, after));
        }

        UUID id = UUID.randomUUID();
        plans.put(id, new Plan(user.getId(), external, List.copyOf(favorites),
                List.copyOf(changedItems), Map.copyOf(before)));
        return new Preview(id, external.username(), Instant.now().plus(Duration.ofMinutes(30)),
                BATCH_SIZE, unchanged, List.copyOf(changes));
    }

    public BatchResult applyBatch(UUID planId, int batchIndex) {
        if (batchIndex < 0) throw new BusinessException("El lote de sincronización no es válido.");
        AppUser user = connectedUser();
        Plan plan = plan(planId, user);
        int from = batchIndex * BATCH_SIZE;
        if (from >= plan.entries.size()) throw new BusinessException("El lote de sincronización no es válido.");
        int to = Math.min(from + BATCH_SIZE, plan.entries.size());
        // Compatibility with clients from 1.13.0: they may send every difference without review.
        // Only additions are safe to apply implicitly; existing AniRecord data must be selected explicitly.
        List<Integer> additions = plan.entries.subList(from, to).stream()
                .map(item -> item.media().anilistId())
                .filter(id -> plan.before.get(id) == null)
                .toList();
        BatchResult result = applySelection(planId, additions);
        return new BatchResult(to - from, result.created(), result.updated());
    }

    public BatchResult applySelection(UUID planId, List<Integer> anilistIds) {
        AppUser user = connectedUser();
        Plan plan = plan(planId, user);
        if (anilistIds == null || anilistIds.size() > BATCH_SIZE)
            throw new BusinessException("El lote de sincronización no es válido.");
        List<Integer> ids = anilistIds.stream().distinct().sorted().toList();
        if (ids.size() != anilistIds.size())
            throw new BusinessException("La selección contiene entradas repetidas.");
        List<AniListLibraryItem> selected = ids.stream().map(id -> {
            AniListLibraryItem item = plan.entriesById.get(id);
            if (item == null) throw new BusinessException("La selección contiene una entrada no válida.");
            return item;
        }).toList();
        String key = ids.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(","));
        synchronized (writeLocks[Math.floorMod(user.getId().hashCode(), writeLocks.length)]) {
            BatchResult completed = plan.completedSelections.get(key);
            if (completed != null) return completed;
            if (ids.stream().anyMatch(plan.appliedIds::contains))
                throw new BusinessException("Una entrada de esta selección ya fue sincronizada.");
            LibraryImportWriter.BatchResult result = writer.applyBatch(user.getId(), selected, plan.before);
            BatchResult response = new BatchResult(result.processed(), result.created(), result.updated());
            plan.appliedIds.addAll(ids);
            plan.completedSelections.put(key, response);
            return response;
        }
    }

    public CompleteResult complete(UUID planId) {
        AppUser user = connectedUser();
        Plan plan = plan(planId, user);
        synchronized (writeLocks[Math.floorMod(user.getId().hashCode(), writeLocks.length)]) {
            LibraryImportWriter.FavoriteResult result = writer.complete(
                    user.getId(), plan.library, plan.favorites);
            plans.invalidate(planId);
            return new CompleteResult(result.created(), result.characters(), result.staff());
        }
    }

    private Plan plan(UUID id, AppUser user) {
        Plan plan = plans.getIfPresent(id);
        if (plan == null || !Objects.equals(plan.userId, user.getId()))
            throw new BusinessException("La sincronización venció. Iníciala de nuevo.");
        return plan;
    }

    private AppUser connectedUser() {
        AppUser user = currentUser.require();
        if (user.getAccountType() != AccountType.ANILIST || user.getAnilistUserId() == null)
            throw new BusinessException("Esta función requiere una cuenta de AniRecord conectada con AniList.");
        return user;
    }

    private String token(AppUser user) {
        String token = auth.validToken(user);
        if (token == null || token.isBlank())
            throw new BusinessException("La autorización de AniList venció. Inicia sesión de nuevo con AniList.");
        return token;
    }

    static LocalState state(LibraryEntrySummaryResponse entry) {
        return new LocalState(entry.progress(), entry.personalScore(), entry.rewatchCount(),
                entry.trackingStatus(), entry.startedAt(), entry.completedAt());
    }

    private static LocalState state(AniListLibraryItem entry) {
        return new LocalState(entry.progress(), entry.personalScore(), entry.rewatchCount(),
                entry.trackingStatus(), entry.startedAt(), entry.completedAt());
    }

    /**
     * AniRecord remains authoritative when replacing it would lose information.
     * AniList may advance progress, fill an empty score, and complete an active
     * entry, but it never lowers progress, replaces a score, or changes an
     * explicit pause/drop. Dates and repeat counts already in AniRecord remain.
     */
    static LocalState merge(LocalState local, LocalState remote, Integer totalUnits) {
        if (local == null) return remote;
        int progress = Math.max(value(local.progress()), value(remote.progress()));
        BigDecimal score = hasScore(local.score()) ? local.score() : remote.score();
        TrackingStatus status = mergedStatus(local.status(), remote.status(), progress, totalUnits);
        return new LocalState(progress, score, local.repeats(), status, local.startedAt(), local.completedAt());
    }

    private static TrackingStatus mergedStatus(TrackingStatus local, TrackingStatus remote,
            int progress, Integer totalUnits) {
        if (local == TrackingStatus.COMPLETED || local == TrackingStatus.PAUSED
                || local == TrackingStatus.DROPPED) return local;
        boolean confirmedCompletion = remote == TrackingStatus.COMPLETED
                && totalUnits != null && progress >= totalUnits;
        if (confirmedCompletion) return TrackingStatus.COMPLETED;
        if (local == TrackingStatus.PLANNED && progress > 0) return TrackingStatus.IN_PROGRESS;
        return local;
    }

    private static AniListLibraryItem withState(AniListLibraryItem item, LocalState state) {
        return new AniListLibraryItem(item.media(), value(state.progress()), state.score(),
                value(state.repeats()), state.status(), state.startedAt(), state.completedAt());
    }

    private static boolean hasScore(BigDecimal score) {
        return score != null && score.signum() > 0;
    }

    private static int value(Integer number) {
        return number == null ? 0 : number;
    }

    private static boolean sameState(LocalState left, LocalState right) {
        return Objects.equals(left.progress(), right.progress())
                && sameScore(left.score(), right.score())
                && Objects.equals(left.repeats(), right.repeats())
                && left.status() == right.status()
                && Objects.equals(left.startedAt(), right.startedAt())
                && Objects.equals(left.completedAt(), right.completedAt());
    }

    private static boolean sameScore(BigDecimal left, BigDecimal right) {
        if (left == null || right == null) return left == right;
        return left.compareTo(right) == 0;
    }
}
