package com.animelist.backend.service;

import com.animelist.backend.dto.anilist.AniListLibraryExport;
import com.animelist.backend.dto.anilist.AniListLibraryItem;
import com.animelist.backend.dto.anilist.AniListPersonFavorite;
import com.animelist.backend.dto.library.LibraryEntrySummaryResponse;
import com.animelist.backend.entity.AppUser;
import com.animelist.backend.enums.AccountType;
import com.animelist.backend.enums.MediaType;
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
    static final int BATCH_SIZE = 20;

    public record LocalState(Integer progress, BigDecimal score, Integer repeats,
            com.animelist.backend.enums.TrackingStatus status, LocalDate startedAt, LocalDate completedAt) {}
    public record Change(int anilistId, String title, String coverImage, MediaType mediaType,
            String mediaFormat, boolean addition, int progress,
            com.animelist.backend.enums.TrackingStatus status, BigDecimal score) {}
    public record Preview(UUID id, String username, Instant expiresAt, int batchSize,
            List<Change> changes) {}
    public record BatchResult(int processed, int created, int updated) {}
    public record CompleteResult(int personFavoritesCreated, int characterFavoritesCreated,
            int staffFavoritesCreated) {}

    private static final class Plan {
        private final Long userId;
        private final AniListLibraryExport library;
        private final List<AniListPersonFavorite> favorites;
        private final List<AniListLibraryItem> entries;
        private final Map<Integer, LocalState> before;
        private final Map<Integer, BatchResult> completedBatches = new ConcurrentHashMap<>();

        private Plan(Long userId, AniListLibraryExport library, List<AniListPersonFavorite> favorites,
                List<AniListLibraryItem> entries, Map<Integer, LocalState> before) {
            this.userId = userId;
            this.library = library;
            this.favorites = favorites;
            this.entries = entries;
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

        List<AniListLibraryItem> items = List.copyOf(external.entries());
        List<Change> changes = new ArrayList<>(items.size());
        for (AniListLibraryItem item : items) {
            var media = item.media();
            changes.add(new Change(media.anilistId(), media.title(), media.coverImage(), media.mediaType(),
                    media.mediaFormat(), !before.containsKey(media.anilistId()), item.progress(),
                    item.trackingStatus(), item.personalScore()));
        }

        UUID id = UUID.randomUUID();
        plans.put(id, new Plan(user.getId(), external, List.copyOf(favorites), items, Map.copyOf(before)));
        return new Preview(id, external.username(), Instant.now().plus(Duration.ofMinutes(30)),
                BATCH_SIZE, List.copyOf(changes));
    }

    public BatchResult applyBatch(UUID planId, int batchIndex) {
        if (batchIndex < 0) throw new BusinessException("El lote de sincronización no es válido.");
        AppUser user = connectedUser();
        Plan plan = plan(planId, user);
        int from = batchIndex * BATCH_SIZE;
        if (from >= plan.entries.size()) throw new BusinessException("El lote de sincronización no es válido.");
        return plan.completedBatches.computeIfAbsent(batchIndex, ignored -> {
            int to = Math.min(from + BATCH_SIZE, plan.entries.size());
            synchronized (writeLocks[Math.floorMod(user.getId().hashCode(), writeLocks.length)]) {
                LibraryImportWriter.BatchResult result = writer.applyBatch(
                        user.getId(), plan.entries.subList(from, to), plan.before);
                return new BatchResult(result.processed(), result.created(), result.updated());
            }
        });
    }

    public CompleteResult complete(UUID planId) {
        AppUser user = connectedUser();
        Plan plan = plan(planId, user);
        int batchCount = (plan.entries.size() + BATCH_SIZE - 1) / BATCH_SIZE;
        if (plan.completedBatches.size() < batchCount)
            throw new BusinessException("Todavía quedan entradas por sincronizar.");
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
}
