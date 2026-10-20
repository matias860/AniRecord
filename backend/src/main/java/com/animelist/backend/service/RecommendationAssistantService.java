package com.animelist.backend.service;

import com.animelist.backend.dto.anilist.AniListMediaResponse;
import com.animelist.backend.dto.anilist.CatalogCategoriesResponse;
import com.animelist.backend.dto.anilist.CatalogTagResponse;
import com.animelist.backend.dto.anilist.MediaTagResponse;
import com.animelist.backend.dto.anilist.MediaRelationResponse;
import com.animelist.backend.dto.assistant.AssistantMediaResponse;
import com.animelist.backend.dto.assistant.AssistantRecommendationResponse;
import com.animelist.backend.dto.assistant.RecommendationIntent;
import com.animelist.backend.dto.library.LibraryEntrySummaryResponse;
import com.animelist.backend.entity.AppUser;
import com.animelist.backend.enums.MediaType;
import com.animelist.backend.enums.InstallmentPreference;
import com.animelist.backend.enums.ReleaseStatus;
import com.animelist.backend.enums.TrackingStatus;
import com.animelist.backend.repository.LibraryEntryRepository;
import java.time.Year;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class RecommendationAssistantService {
    private static final Logger log = LoggerFactory.getLogger(RecommendationAssistantService.class);
    private static final Set<String> SORTS = Set.of("POPULARITY", "SCORE", "TRENDING", "NEWEST");
    private static final Set<String> COUNTRIES = Set.of("JP", "KR", "CN");
    private static final Set<String> ANIME_FORMATS = Set.of("TV", "TV_SHORT", "MOVIE", "SPECIAL", "OVA", "ONA", "MUSIC");
    private static final Set<String> MANGA_FORMATS = Set.of("MANGA", "NOVEL", "ONE_SHOT");
    private static final Set<String> FRANCHISE_RELATIONS = Set.of(
            "ADAPTATION", "PREQUEL", "SEQUEL", "PARENT", "ALTERNATIVE",
            "SPIN_OFF", "SIDE_STORY", "SUMMARY", "COMPILATION", "CONTAINS", "SOURCE");
    private static final int MIN_REQUIRED_TAG_RANK = 50;
    private static final int MIN_PRIMARY_GENRE_SCORE = 60;
    private static final int MAX_CONTEXTUAL_PENALTY = 50;
    private static final Map<String, Map<String, Integer>> RELATED_GENRE_PENALTIES = Map.of(
            "drama", Map.of("psychological", 38, "horror", 32, "thriller", 28, "mystery", 18),
            "horror", Map.of("psychological", 30, "thriller", 24, "mystery", 16),
            "ecchi", Map.of("romance", 8));
    private static final Map<String, Map<String, Integer>> RELATED_TAG_PENALTIES = Map.of(
            "drama", Map.of("tragedy", 65, "suicide", 65, "bullying", 45, "revenge", 35,
                    "death", 30, "war", 24, "survival", 20),
            "horror", Map.of("body horror", 60, "gore", 55, "survival", 28, "death", 25),
            "ecchi", Map.of("nudity", 65, "fanservice", 55, "sexual content", 65));
    private static final Set<String> ROMANCE_THEME_TAGS = Set.of(
            "heterosexual", "love triangle", "unrequited love", "yuri", "boys' love",
            "female harem", "male harem", "marriage", "age gap");
    private static final Map<String, Integer> ROMANCE_DISTRACTOR_PENALTIES = Map.of(
            "action", 15, "adventure", 10, "horror", 25, "psychological", 20,
            "thriller", 20, "ecchi", 10);

    private final CurrentUserService currentUserService;
    private final LibraryEntryRepository libraryEntries;
    private final AniListClient aniListClient;
    private final LocalRecommendationInterpreter localInterpreter;
    private final GeminiRecommendationClient gemini;
    private final AssistantQuotaService quota;

    public RecommendationAssistantService(CurrentUserService currentUserService, LibraryEntryRepository libraryEntries,
            AniListClient aniListClient, LocalRecommendationInterpreter localInterpreter,
            GeminiRecommendationClient gemini, AssistantQuotaService quota) {
        this.currentUserService = currentUserService;
        this.libraryEntries = libraryEntries;
        this.aniListClient = aniListClient;
        this.localInterpreter = localInterpreter;
        this.gemini = gemini;
        this.quota = quota;
    }

    public AssistantRecommendationResponse recommend(String text, RecommendationIntent previous) {
        long started = System.nanoTime();
        AppUser user = currentUserService.require();
        CatalogCategoriesResponse categories = safeCategories();
        RecommendationIntent safePrevious = previous == null ? null : sanitize(previous, categories);
        List<String> allowedTags = categories.tags().stream().map(CatalogTagResponse::name).toList();
        LocalRecommendationInterpreter.ParseResult local = localInterpreter.interpret(text.trim(), safePrevious, true, allowedTags);
        RecommendationIntent raw = local.intent();
        String source = "LOCAL";
        if (!local.complete()) {
            if (gemini.available() && quota.tryConsume(user.getId())) {
                try {
                    raw = mergeExplicitConstraints(
                            gemini.interpret(text.trim(), safePrevious, categories.genres(), allowedTags),
                            local.intent(), local.libraryScopeSpecified());
                    source = "GEMINI";
                } catch (GeminiRecommendationClient.BusyException exception) {
                    quota.refund(user.getId());
                    source = "FALLBACK";
                    raw = localInterpreter.interpret(text.trim(), safePrevious, false, allowedTags).intent();
                } catch (RuntimeException exception) {
                    source = "FALLBACK";
                    log.warn("Assistant interpretation fallback: {}", exception.getClass().getSimpleName());
                    raw = localInterpreter.interpret(text.trim(), safePrevious, false, allowedTags).intent();
                }
            } else {
                source = "FALLBACK";
                raw = localInterpreter.interpret(text.trim(), safePrevious, false, allowedTags).intent();
            }
        }
        RecommendationIntent intent;
        try {
            intent = sanitize(raw, categories);
        } catch (RuntimeException invalidModelOutput) {
            source = "FALLBACK";
            intent = sanitize(localInterpreter.interpret(text.trim(), safePrevious, false, allowedTags).intent(), categories);
        }
        List<LibraryEntrySummaryResponse> library = libraryEntries.findSummaries(user.getId(), intent.mediaType(), null);
        boolean unsupportedFallback = !local.complete() && !hasConcreteFilter(intent);
        if (unsupportedFallback) {
            source = "FALLBACK";
            return new AssistantRecommendationResponse(source,
                    "No pude interpretar ese matiz con seguridad. Probá agregar un género, una duración o un título de referencia.",
                    intent, List.of(), quota.remaining(user.getId()), understood(intent, false));
        }
        CatalogSelection selection = intent.libraryOnly()
                ? fromLibrary(intent, library)
                : fromCatalog(intent, library);
        List<AssistantMediaResponse> results = selection.results();
        String reply = intent.referenceTitle() != null && !selection.referenceResolved()
                ? "No pude encontrar el título de referencia. Revisá cómo está escrito o probá con su nombre original."
                : results.isEmpty()
                ? "No encontré una opción que cumpla todo. Probá quitando un filtro o describiendo qué aspecto es más importante."
                : results.size() == 1 ? "Encontré una opción que encaja con lo que pediste."
                : "Encontré " + results.size() + " opciones verificadas que encajan con lo que pediste.";
        if ("FALLBACK".equals(source) && !results.isEmpty()) reply = "Usé solamente los filtros que pude comprobar. " + reply;
        log.info("Assistant response source={} results={} elapsedMs={}", source, results.size(), (System.nanoTime() - started) / 1_000_000);
        return new AssistantRecommendationResponse(source, reply, intent, results, quota.remaining(user.getId()), understood(intent, !results.isEmpty()));
    }

    private CatalogCategoriesResponse safeCategories() {
        try {
            return aniListClient.getCategories();
        } catch (RuntimeException exception) {
            log.warn("Assistant category fallback: {}", exception.getClass().getSimpleName());
            return new CatalogCategoriesResponse(List.of("Action", "Adventure", "Comedy", "Drama", "Ecchi", "Fantasy",
                    "Horror", "Mahou Shoujo", "Mecha", "Music", "Mystery", "Psychological", "Romance", "Sci-Fi",
                    "Slice of Life", "Sports", "Supernatural", "Thriller"),
                    List.of(new CatalogTagResponse("Yuri", "", "Theme-Sexuality"),
                            new CatalogTagResponse("Love Triangle", "", "Theme-Romance"),
                            new CatalogTagResponse("Unrequited Love", "", "Theme-Romance"),
                            new CatalogTagResponse("Female Harem", "", "Theme-Romance"),
                            new CatalogTagResponse("Male Harem", "", "Theme-Romance")));
        }
    }

    private RecommendationIntent mergeExplicitConstraints(RecommendationIntent interpreted, RecommendationIntent explicit,
            boolean libraryScopeSpecified) {
        if (interpreted == null) interpreted = RecommendationIntent.empty();
        List<String> excludedGenres = merge(safeValues(interpreted.excludedGenres()), safeValues(explicit.excludedGenres()), 10);
        List<String> excludedTags = merge(safeValues(interpreted.excludedTags()), safeValues(explicit.excludedTags()), 12);
        List<String> genres = merge(safeValues(interpreted.genres()), safeValues(explicit.genres()), 10).stream()
                .filter(value -> !containsIgnoreCase(excludedGenres, value)).toList();
        List<String> primaryGenres = merge(safeValues(interpreted.primaryGenres()), safeValues(explicit.primaryGenres()), 10).stream()
                .filter(value -> containsIgnoreCase(genres, value)).toList();
        List<String> tags = merge(safeValues(interpreted.tags()), safeValues(explicit.tags()), 12).stream()
                .filter(value -> !containsIgnoreCase(excludedTags, value)).toList();
        List<MediaType> excludedMediaTypes = merge(safeValues(interpreted.excludedMediaTypes()),
                safeValues(explicit.excludedMediaTypes()), 2);
        List<ReleaseStatus> excludedStatuses = merge(safeValues(interpreted.excludedStatuses()),
                safeValues(explicit.excludedStatuses()), 5);
        List<String> excludedCountries = merge(safeValues(interpreted.excludedCountries()),
                safeValues(explicit.excludedCountries()), 3);
        List<String> excludedFormats = merge(safeValues(interpreted.excludedFormats()),
                safeValues(explicit.excludedFormats()), 10);
        return new RecommendationIntent(
                explicit.mediaType() != null ? explicit.mediaType() : interpreted.mediaType(),
                excludedMediaTypes, genres, primaryGenres, tags, excludedGenres, excludedTags,
                explicit.status() != null ? explicit.status() : interpreted.status(),
                excludedStatuses,
                explicit.country() != null ? explicit.country() : interpreted.country(),
                excludedCountries,
                explicit.format() != null ? explicit.format() : interpreted.format(),
                excludedFormats,
                interpreted.sort(),
                explicit.yearFrom() != null ? explicit.yearFrom() : interpreted.yearFrom(),
                explicit.yearTo() != null ? explicit.yearTo() : interpreted.yearTo(),
                explicit.season() != null ? explicit.season() : interpreted.season(),
                explicit.minimumUnits() != null ? explicit.minimumUnits() : interpreted.minimumUnits(),
                explicit.maximumUnits() != null ? explicit.maximumUnits() : interpreted.maximumUnits(),
                explicit.referenceTitle() != null ? explicit.referenceTitle() : interpreted.referenceTitle(),
                explicitlySelectsInstallment(explicit) ? explicit.installmentPreference() : interpreted.installmentPreference(),
                explicit.standaloneOnly() || interpreted.standaloneOnly(),
                libraryScopeSpecified ? explicit.libraryOnly() : explicit.libraryOnly() || interpreted.libraryOnly(),
                libraryScopeSpecified ? explicit.libraryStatus()
                        : explicit.libraryStatus() != null ? explicit.libraryStatus() : interpreted.libraryStatus());
    }

    private CatalogSelection fromCatalog(RecommendationIntent intent, List<LibraryEntrySummaryResponse> library) {
        Set<Integer> known = library.stream().map(LibraryEntrySummaryResponse::anilistId).collect(java.util.stream.Collectors.toSet());
        AniListMediaResponse reference = findReference(intent, library);
        if (intent.referenceTitle() != null && reference == null) return new CatalogSelection(List.of(), false);
        Set<Integer> referenceFamily = referenceFamily(reference);
        List<String> referenceGenres = reference == null ? List.of() : reference.genres();
        List<String> referenceTags = reference == null ? List.of() : reference.tags().stream()
                .filter(tag -> tag.rank() == null || tag.rank() >= 60).map(tag -> tag.name()).toList();
        List<String> queryGenres = intent.genres().isEmpty() ? referenceGenres.stream().limit(2).toList() : intent.genres();
        List<String> queryTags = intent.tags();
        List<String> scoringGenres = merge(intent.genres(), referenceGenres, 8);
        List<String> scoringTags = merge(intent.tags(), referenceTags, 10);
        List<AniListMediaResponse> candidates = browseCatalog(intent, queryGenres, queryTags);
        Map<String, Integer> preferences = preferences(library);
        Integer referenceId = reference == null ? null : reference.anilistId();
        List<AniListMediaResponse> ranked = candidates.stream()
                .filter(item -> !known.contains(item.anilistId()) && !item.anilistId().equals(referenceId))
                .filter(item -> java.util.Collections.disjoint(franchiseFootprint(item), referenceFamily))
                .filter(item -> matchesInstallment(item, intent.installmentPreference()))
                .filter(item -> !intent.standaloneOnly() || isStandalone(item))
                .filter(item -> !intent.excludedMediaTypes().contains(item.mediaType()))
                .filter(item -> !intent.excludedStatuses().contains(item.releaseStatus()))
                .filter(item -> intent.country() == null || intent.country().equals(item.countryOfOrigin()))
                .filter(item -> intent.format() == null || intent.format().equals(item.mediaFormat()))
                .filter(item -> !containsIgnoreCase(intent.excludedCountries(), item.countryOfOrigin()))
                .filter(item -> !containsIgnoreCase(intent.excludedFormats(), item.mediaFormat()))
                .filter(item -> fits(item.totalUnits(), intent.minimumUnits(), intent.maximumUnits()))
                .filter(item -> containsAll(item.genres(), intent.genres()))
                .filter(item -> hasCentralRequiredGenres(item, intent.primaryGenres()))
                .filter(item -> hasRelevantRequiredTags(item.tags(), intent.tags()))
                .filter(item -> disjoint(item.genres(), intent.excludedGenres()))
                .filter(item -> !hasRelevantExcludedTag(item.tags(), intent.excludedTags()))
                .filter(item -> contextualPenalty(item, intent) <= MAX_CONTEXTUAL_PENALTY)
                .sorted(Comparator.comparingInt((AniListMediaResponse item) -> score(item, intent, scoringGenres, scoringTags, preferences))
                        .thenComparingInt(item -> item.favourites() == null ? 0 : item.favourites()).reversed())
                .toList();
        List<AssistantMediaResponse> results = distinctFranchises(ranked).stream().limit(6)
                .map(item -> toResponse(item, null, reason(item, intent, reference))).toList();
        return new CatalogSelection(results, true);
    }

    private List<AniListMediaResponse> browseCatalog(RecommendationIntent intent, List<String> genres, List<String> tags) {
        return aniListClient.browseForAssistant(intent.mediaType(), 45, intent.yearFrom(), intent.yearTo(),
                intent.season(), genres, tags, intent.status(), intent.country(), intent.format(), intent.sort()).results();
    }

    private CatalogSelection fromLibrary(RecommendationIntent intent, List<LibraryEntrySummaryResponse> library) {
        AniListMediaResponse reference = findReference(intent, library);
        if (intent.referenceTitle() != null && reference == null) return new CatalogSelection(List.of(), false);
        List<LibraryEntrySummaryResponse> candidates = library.stream()
                .filter(item -> intent.libraryStatus() == null || item.trackingStatus() == intent.libraryStatus())
                .filter(item -> !intent.excludedMediaTypes().contains(item.mediaType()))
                .filter(item -> fits(item.totalUnits(), intent.minimumUnits(), intent.maximumUnits()))
                .filter(item -> containsAll(split(item.genres()), intent.genres()))
                .filter(item -> disjoint(split(item.genres()), intent.excludedGenres()))
                .filter(item -> intent.status() == null || item.releaseStatus() == intent.status())
                .filter(item -> !intent.excludedStatuses().contains(item.releaseStatus()))
                .filter(item -> intent.format() == null || intent.format().equals(item.mediaFormat()))
                .filter(item -> !containsIgnoreCase(intent.excludedFormats(), item.mediaFormat()))
                .filter(item -> intent.country() == null || intent.country().equals(item.countryOfOrigin()))
                .filter(item -> !containsIgnoreCase(intent.excludedCountries(), item.countryOfOrigin()))
                .filter(item -> intent.season() == null || intent.season() == item.releaseSeason())
                .filter(item -> intent.yearFrom() == null || item.releaseYear() != null && item.releaseYear() >= intent.yearFrom())
                .filter(item -> intent.yearTo() == null || item.releaseYear() != null && item.releaseYear() <= intent.yearTo())
                .filter(item -> reference == null || !item.anilistId().equals(reference.anilistId()))
                .filter(item -> reference == null || sharesElements(item, reference))
                .sorted(Comparator.comparingInt((LibraryEntrySummaryResponse item) -> libraryScore(item, reference)).reversed())
                .limit(24).toList();
        Map<Integer, AniListMediaResponse> verifiedDetails;
        try {
            verifiedDetails = aniListClient.getMediaForAssistant(candidates.stream()
                    .map(LibraryEntrySummaryResponse::anilistId).toList()).stream()
                    .collect(java.util.stream.Collectors.toMap(AniListMediaResponse::anilistId, item -> item));
        } catch (RuntimeException exception) {
            log.debug("Could not verify library recommendations", exception);
            verifiedDetails = Map.of();
        }
        List<AssistantMediaResponse> results = new ArrayList<>();
        for (LibraryEntrySummaryResponse item : candidates) {
            AniListMediaResponse details = verifiedDetails.get(item.anilistId());
            if (details == null || !matchesInstallment(details, intent.installmentPreference())
                    || intent.standaloneOnly() && !isStandalone(details)
                    || !hasCentralRequiredGenres(details, intent.primaryGenres())
                    || !hasRelevantRequiredTags(details.tags(), intent.tags())
                    || hasRelevantExcludedTag(details.tags(), intent.excludedTags())
                    || contextualPenalty(details, intent) > MAX_CONTEXTUAL_PENALTY) continue;
            results.add(new AssistantMediaResponse(item.anilistId(), item.mediaType(), item.mediaFormat(), item.countryOfOrigin(), item.title(),
                    item.romajiTitle(), item.coverImage(), item.totalUnits(), item.releaseStatus(), item.releaseYear(),
                    details.genres(), details.tags().stream().map(MediaTagResponse::name).toList(), item.trackingStatus(),
                    libraryReason(item, reference)));
            if (results.size() == 6) break;
        }
        return new CatalogSelection(results, true);
    }

    private AniListMediaResponse findReference(RecommendationIntent intent, List<LibraryEntrySummaryResponse> library) {
        if (intent.referenceTitle() == null) return null;
        try {
            LibraryEntrySummaryResponse saved = bestLibraryReference(library, intent.referenceTitle(), intent.mediaType());
            if (saved != null) return aniListClient.getMedia(saved.anilistId());
            if (intent.mediaType() != null) {
                return bestReference(aniListClient.search(intent.referenceTitle(), intent.mediaType(), 1, 5).results(), intent.referenceTitle());
            }
            List<AniListMediaResponse> candidates = new ArrayList<>();
            candidates.addAll(aniListClient.search(intent.referenceTitle(), MediaType.ANIME, 1, 5).results());
            candidates.addAll(aniListClient.search(intent.referenceTitle(), MediaType.MANGA, 1, 5).results());
            return bestReference(candidates, intent.referenceTitle());
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private AniListMediaResponse bestReference(List<AniListMediaResponse> results, String title) {
        return results.stream()
                .map(item -> Map.entry(item, Math.max(titleScore(title, item.title()), titleScore(title, item.romajiTitle()))))
                .filter(entry -> entry.getValue() >= 82)
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey).orElse(null);
    }

    private LibraryEntrySummaryResponse bestLibraryReference(List<LibraryEntrySummaryResponse> library, String title, MediaType type) {
        return library.stream().filter(item -> type == null || item.mediaType() == type)
                .map(item -> Map.entry(item, Math.max(titleScore(title, item.title()), titleScore(title, item.romajiTitle()))))
                .filter(entry -> entry.getValue() >= 82)
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey).orElse(null);
    }

    private int titleScore(String requested, String candidate) {
        String left = normalizedTitle(requested), right = normalizedTitle(candidate);
        if (left.isBlank() || right.isBlank()) return 0;
        if (left.equals(right)) return 100;
        if (left.length() >= 5 && (right.startsWith(left + " ") || right.endsWith(" " + left)
                || right.contains(" " + left + " "))) return 92;
        int distance = editDistance(left, right);
        return (int) Math.round(100d * (1d - (double) distance / Math.max(left.length(), right.length())));
    }

    private String normalizedTitle(String value) {
        if (value == null) return "";
        return Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").trim().replaceAll("\\s+", " ");
    }

    private int editDistance(String left, String right) {
        int[] previous = new int[right.length() + 1];
        for (int index = 0; index <= right.length(); index++) previous[index] = index;
        for (int row = 1; row <= left.length(); row++) {
            int[] current = new int[right.length() + 1];
            current[0] = row;
            for (int column = 1; column <= right.length(); column++) {
                int substitution = previous[column - 1] + (left.charAt(row - 1) == right.charAt(column - 1) ? 0 : 1);
                current[column] = Math.min(Math.min(previous[column] + 1, current[column - 1] + 1), substitution);
            }
            previous = current;
        }
        return previous[right.length()];
    }

    private RecommendationIntent sanitize(RecommendationIntent raw, CatalogCategoriesResponse categories) {
        if (raw == null) raw = RecommendationIntent.empty();
        MediaType type = raw.mediaType();
        Map<String, String> genres = canonical(categories.genres());
        Map<String, String> tags = canonical(categories.tags().stream().map(item -> item.name()).toList());
        int maximumYear = Year.now().getValue() + 3;
        Integer from = clamp(raw.yearFrom(), 1940, maximumYear);
        Integer to = clamp(raw.yearTo(), 1940, maximumYear);
        if (from != null && to != null && from > to) { int swap = from; from = to; to = swap; }
        String format = upper(raw.format());
        if (format != null && type == null) {
            if (ANIME_FORMATS.contains(format)) type = MediaType.ANIME;
            else if (MANGA_FORMATS.contains(format)) type = MediaType.MANGA;
            else format = null;
        } else if (format != null && !(type == MediaType.ANIME ? ANIME_FORMATS : MANGA_FORMATS).contains(format)) {
            format = null;
        }
        String country = upper(raw.country());
        if (country == null || !COUNTRIES.contains(country)) country = null;
        String sort = upper(raw.sort());
        if (sort == null || !SORTS.contains(sort)) sort = "POPULARITY";
        Integer min = clamp(raw.minimumUnits(), 1, 10_000), max = clamp(raw.maximumUnits(), 1, 10_000);
        if (min != null && max != null && min > max) { int swap = min; min = max; max = swap; }
        String reference = raw.referenceTitle() == null ? null : raw.referenceTitle().trim();
        if (reference != null && reference.length() > 100) reference = reference.substring(0, 100);
        if (reference != null && reference.isBlank()) reference = null;
        List<String> validGenres = valid(raw.genres(), genres, 4);
        List<String> primaryGenres = valid(raw.primaryGenres(), genres, 4).stream()
                .filter(value -> containsIgnoreCase(validGenres, value)).toList();
        List<String> excludedCountries = safeValues(raw.excludedCountries()).stream().map(RecommendationAssistantService::upper)
                .filter(COUNTRIES::contains).distinct().limit(3).toList();
        List<String> excludedFormats = safeValues(raw.excludedFormats()).stream().map(RecommendationAssistantService::upper)
                .filter(value -> ANIME_FORMATS.contains(value) || MANGA_FORMATS.contains(value)).distinct().limit(10).toList();
        List<MediaType> excludedTypes = safeValues(raw.excludedMediaTypes()).stream().distinct().limit(2).toList();
        List<ReleaseStatus> excludedStatuses = safeValues(raw.excludedStatuses()).stream().distinct().limit(5).toList();
        ReleaseStatus status = raw.status();
        if (excludedTypes.contains(type)) type = null;
        if (excludedStatuses.contains(status)) status = null;
        if (containsIgnoreCase(excludedCountries, country)) country = null;
        if (containsIgnoreCase(excludedFormats, format)) format = null;
        InstallmentPreference installment = raw.installmentPreference() == null ? InstallmentPreference.FIRST : raw.installmentPreference();
        return new RecommendationIntent(type, excludedTypes,
                validGenres, primaryGenres, valid(raw.tags(), tags, 5),
                valid(raw.excludedGenres(), genres, 4), valid(raw.excludedTags(), tags, 5), status,
                excludedStatuses, country, excludedCountries,
                format, excludedFormats, sort, from, to, raw.season(), min, max, reference, installment, raw.standaloneOnly(),
                raw.libraryOnly(), raw.libraryStatus());
    }

    private Map<String, Integer> preferences(List<LibraryEntrySummaryResponse> library) {
        Map<String, Integer> result = new HashMap<>();
        for (LibraryEntrySummaryResponse item : library) {
            int weight = item.trackingStatus() == TrackingStatus.COMPLETED ? 3 : item.trackingStatus() == TrackingStatus.IN_PROGRESS ? 2 : 1;
            if (item.personalScore() != null && item.personalScore().doubleValue() >= 8) weight += 3;
            for (String genre : split(item.genres())) result.merge(genre.toLowerCase(Locale.ROOT), weight, Integer::sum);
            for (String tag : split(item.tags())) result.merge(tag.toLowerCase(Locale.ROOT), weight, Integer::sum);
        }
        return result;
    }

    private int score(AniListMediaResponse item, RecommendationIntent intent, List<String> genres, List<String> tags,
            Map<String, Integer> preferences) {
        int score = 0;
        for (String genre : item.genres()) {
            if (containsIgnoreCase(genres, genre)) score += 30;
            score += Math.min(8, preferences.getOrDefault(genre.toLowerCase(Locale.ROOT), 0));
        }
        for (var tag : item.tags()) {
            if (containsIgnoreCase(tags, tag.name())) score += relevance(tag);
            score += Math.min(5, preferences.getOrDefault(tag.name().toLowerCase(Locale.ROOT), 0));
        }
        return score - contextualPenalty(item, intent);
    }

    private String reason(AniListMediaResponse item, RecommendationIntent intent, AniListMediaResponse reference) {
        List<String> facts = new ArrayList<>();
        List<String> primaryMatches = item.genres().stream()
                .filter(genre -> containsIgnoreCase(intent.primaryGenres(), genre)).limit(2).toList();
        if (!primaryMatches.isEmpty()) facts.add(String.join(" y ", primaryMatches) + " es uno de sus ejes principales");
        List<String> matches = item.genres().stream().filter(genre -> containsIgnoreCase(intent.genres(), genre)
                && !containsIgnoreCase(intent.primaryGenres(), genre)).limit(2).toList();
        if (!matches.isEmpty()) facts.add("combina " + String.join(" y ", matches));
        List<MediaTagResponse> matchingTags = item.tags().stream()
                .filter(tag -> containsIgnoreCase(intent.tags(), tag.name())).limit(2).toList();
        if (!matchingTags.isEmpty()) {
            facts.add(matchingTags.stream().map(tag -> displayTag(tag.name()) + " tiene " + relevance(tag)
                    + "% de relevancia en AniList").collect(java.util.stream.Collectors.joining(" y ")));
        }
        if (reference != null && sharesElements(item, reference)) facts.add("comparte elementos con " + reference.title());
        if (item.totalUnits() != null) facts.add("tiene " + item.totalUnits() + unitLabel(item.mediaType(), item.totalUnits()));
        if (item.releaseStatus() == ReleaseStatus.FINISHED) facts.add("está terminada");
        if (item.releaseStatus() == ReleaseStatus.RELEASING) facts.add("está en emisión");
        if (facts.isEmpty()) facts.add("encaja con los filtros que elegiste");
        String result = String.join(", ", facts);
        return Character.toUpperCase(result.charAt(0)) + result.substring(1) + ".";
    }

    private String libraryReason(LibraryEntrySummaryResponse item, AniListMediaResponse reference) {
        String state = switch (item.trackingStatus()) {
            case PLANNED -> "La tenés pendiente"; case PAUSED -> "La dejaste en pausa";
            case IN_PROGRESS -> "Ya la empezaste"; case COMPLETED -> "Ya la completaste"; case DROPPED -> "La habías abandonado";
        };
        String comparison = reference == null ? "" : "Comparte elementos con " + reference.title() + ". ";
        return comparison + (item.totalUnits() == null ? state + "." : state + " y tiene " + item.totalUnits() + unitLabel(item.mediaType(), item.totalUnits()) + ".");
    }

    private String unitLabel(MediaType type, int total) {
        if (type == MediaType.ANIME) return total == 1 ? " episodio" : " episodios";
        return total == 1 ? " capítulo" : " capítulos";
    }

    private boolean sharesElements(AniListMediaResponse item, AniListMediaResponse reference) {
        if (item.genres().stream().anyMatch(genre -> containsIgnoreCase(reference.genres(), genre))) return true;
        List<String> referenceTags = reference.tags().stream().map(tag -> tag.name()).toList();
        return item.tags().stream().map(tag -> tag.name()).anyMatch(tag -> containsIgnoreCase(referenceTags, tag));
    }

    private boolean sharesElements(LibraryEntrySummaryResponse item, AniListMediaResponse reference) {
        if (split(item.genres()).stream().anyMatch(genre -> containsIgnoreCase(reference.genres(), genre))) return true;
        List<String> referenceTags = reference.tags().stream().map(tag -> tag.name()).toList();
        return split(item.tags()).stream().anyMatch(tag -> containsIgnoreCase(referenceTags, tag));
    }

    private AssistantMediaResponse toResponse(AniListMediaResponse item, TrackingStatus libraryStatus, String reason) {
        return new AssistantMediaResponse(item.anilistId(), item.mediaType(), item.mediaFormat(), item.countryOfOrigin(), item.title(), item.romajiTitle(),
                item.coverImage(), item.totalUnits(), item.releaseStatus(), item.releaseYear(), item.genres(),
                item.tags().stream().map(tag -> tag.name()).toList(), libraryStatus, reason);
    }

    private List<String> understood(RecommendationIntent intent, boolean referenceApplied) {
        List<String> result = new ArrayList<>();
        boolean regionalComic = intent.mediaType() == MediaType.MANGA && !"NOVEL".equals(intent.format())
                && ("KR".equals(intent.country()) || "CN".equals(intent.country()));
        boolean comicPublication = intent.mediaType() == MediaType.MANGA && !"NOVEL".equals(intent.format())
                && (regionalComic || "JP".equals(intent.country()));
        if (intent.mediaType() != null) {
            if (intent.mediaType() == MediaType.ANIME) result.add("Anime");
            else if ("NOVEL".equals(intent.format())) result.add("Novela ligera");
            else if ("KR".equals(intent.country())) result.add("Manhwa");
            else if ("CN".equals(intent.country())) result.add("Manhua");
            else result.add("Manga");
        }
        if (intent.format() != null && !"NOVEL".equals(intent.format())) result.add(formatLabel(intent.format()));
        if (intent.country() != null && !regionalComic && !(intent.mediaType() == MediaType.MANGA && "JP".equals(intent.country()))) {
            result.add(countryName(intent.country()));
        }
        intent.genres().stream().map(genre -> containsIgnoreCase(intent.primaryGenres(), genre)
                ? genre + " principal" : genre).forEach(result::add);
        intent.tags().stream().limit(2).map(this::displayTag).forEach(result::add);
        intent.excludedGenres().stream().limit(2).map(value -> "Sin " + value).forEach(result::add);
        intent.excludedTags().stream().limit(2).map(value -> "Sin " + value).forEach(result::add);
        intent.excludedMediaTypes().stream().limit(1).map(value -> value == MediaType.ANIME ? "Sin anime" : "Sin manga").forEach(result::add);
        intent.excludedCountries().stream().limit(1).map(value -> "Sin obras de " + countryName(value)).forEach(result::add);
        intent.excludedFormats().stream().filter(value -> !(comicPublication && "NOVEL".equals(value))).limit(1)
                .map(value -> "Sin " + formatName(value)).forEach(result::add);
        if (intent.minimumUnits() != null) result.add("Desde " + intent.minimumUnits() + " unidades");
        if (intent.maximumUnits() != null) result.add("Hasta " + intent.maximumUnits() + " unidades");
        if (intent.status() == ReleaseStatus.FINISHED) result.add("Terminadas");
        if (intent.status() == ReleaseStatus.RELEASING) result.add("En emisión");
        if (intent.status() == ReleaseStatus.NOT_YET_RELEASED) result.add("Próximamente");
        if (intent.excludedStatuses().contains(ReleaseStatus.FINISHED)) result.add("Sin terminar");
        if (intent.installmentPreference() == InstallmentPreference.SEQUEL) result.add("Continuaciones");
        if (intent.standaloneOnly()) result.add("Sin continuaciones");
        if (intent.libraryOnly()) result.add("De tu biblioteca");
        if (referenceApplied && intent.referenceTitle() != null) result.add("Como " + intent.referenceTitle());
        return result.stream().distinct().limit(6).toList();
    }

    private boolean hasConcreteFilter(RecommendationIntent intent) {
        return intent.libraryOnly() || intent.mediaType() != null || intent.referenceTitle() != null
                || intent.installmentPreference() == InstallmentPreference.SEQUEL || intent.standaloneOnly()
                || !intent.genres().isEmpty() || !intent.tags().isEmpty()
                || !intent.excludedGenres().isEmpty() || !intent.excludedTags().isEmpty()
                || !intent.excludedMediaTypes().isEmpty() || !intent.excludedStatuses().isEmpty()
                || !intent.excludedCountries().isEmpty() || !intent.excludedFormats().isEmpty()
                || intent.status() != null || intent.format() != null || intent.country() != null || intent.yearFrom() != null
                || intent.yearTo() != null || intent.minimumUnits() != null || intent.maximumUnits() != null;
    }

    private int libraryScore(LibraryEntrySummaryResponse item, AniListMediaResponse reference) {
        int score = item.personalScore() == null ? 0 : item.personalScore().intValue();
        if (reference != null) {
            score += (int) split(item.genres()).stream().filter(genre -> containsIgnoreCase(reference.genres(), genre)).count() * 20;
            List<String> referenceTags = reference.tags().stream().map(tag -> tag.name()).toList();
            score += (int) split(item.tags()).stream().filter(tag -> containsIgnoreCase(referenceTags, tag)).count() * 10;
        }
        return score;
    }
    private int contextualPenalty(AniListMediaResponse item, RecommendationIntent intent) {
        int penalty = 0;
        for (String excluded : intent.excludedGenres()) {
            String key = excluded.toLowerCase(Locale.ROOT);
            Map<String, Integer> relatedGenres = RELATED_GENRE_PENALTIES.getOrDefault(key, Map.of());
            for (String genre : item.genres()) {
                penalty += relatedGenres.getOrDefault(genre.toLowerCase(Locale.ROOT), 0);
            }
            Map<String, Integer> relatedTags = RELATED_TAG_PENALTIES.getOrDefault(key, Map.of());
            for (MediaTagResponse tag : item.tags()) {
                int weight = relatedTags.getOrDefault(tag.name().toLowerCase(Locale.ROOT), 0);
                penalty += weight * relevance(tag) / 100;
            }
        }
        return penalty;
    }
    private Set<Integer> referenceFamily(AniListMediaResponse reference) {
        if (reference == null) return Set.of();
        Set<Integer> family = new HashSet<>(franchiseFootprint(reference));
        List<Integer> directRelations = family.stream().filter(id -> !id.equals(reference.anilistId())).toList();
        try {
            aniListClient.getMediaRelations(directRelations).forEach(response -> {
                if (!family.contains(response.sourceAnilistId())) return;
                response.relations().stream().filter(this::isFranchiseRelation)
                        .map(MediaRelationResponse::anilistId).filter(java.util.Objects::nonNull).forEach(family::add);
            });
        } catch (RuntimeException exception) {
            log.debug("Could not expand the reference franchise; direct relations will still be excluded", exception);
        }
        return Set.copyOf(family);
    }
    private List<AniListMediaResponse> distinctFranchises(List<AniListMediaResponse> ranked) {
        List<AniListMediaResponse> unique = new ArrayList<>();
        List<Set<Integer>> seenFamilies = new ArrayList<>();
        for (AniListMediaResponse candidate : ranked) {
            Set<Integer> footprint = franchiseFootprint(candidate);
            if (seenFamilies.stream().anyMatch(seen -> !java.util.Collections.disjoint(seen, footprint))) continue;
            unique.add(candidate);
            seenFamilies.add(footprint);
        }
        return unique;
    }
    private Set<Integer> franchiseFootprint(AniListMediaResponse media) {
        if (media == null) return Set.of();
        Set<Integer> result = new HashSet<>();
        if (media.anilistId() != null) result.add(media.anilistId());
        if (media.relations() != null) {
            media.relations().stream().filter(this::isFranchiseRelation)
                    .map(MediaRelationResponse::anilistId).filter(java.util.Objects::nonNull).forEach(result::add);
        }
        return Set.copyOf(result);
    }
    private boolean isFranchiseRelation(MediaRelationResponse relation) {
        return relation != null && relation.relationType() != null && FRANCHISE_RELATIONS.contains(relation.relationType());
    }
    private String displayTag(String tag) {
        return "Love Triangle".equalsIgnoreCase(tag) ? "Triángulo amoroso" : tag;
    }
    private String countryName(String country) {
        return switch (country) { case "JP" -> "Japón"; case "KR" -> "Corea"; case "CN" -> "China"; default -> country; };
    }
    private String formatName(String format) {
        return switch (format) { case "MOVIE" -> "películas"; case "NOVEL" -> "novelas"; case "ONE_SHOT" -> "one-shots"; default -> format; };
    }
    private String formatLabel(String format) {
        return switch (format) { case "MOVIE" -> "Película"; case "NOVEL" -> "Novela ligera"; case "ONE_SHOT" -> "One-shot"; default -> format; };
    }
    private boolean matchesInstallment(AniListMediaResponse media, InstallmentPreference preference) {
        InstallmentPreference effective = preference == null ? InstallmentPreference.FIRST : preference;
        return switch (effective) {
            case FIRST -> isEntryPoint(media);
            case SEQUEL -> media.relations() != null && media.relations().stream()
                    .anyMatch(relation -> relation != null && "PREQUEL".equals(relation.relationType()));
            case ANY -> true;
        };
    }
    private boolean explicitlySelectsInstallment(RecommendationIntent intent) {
        return intent != null && (intent.standaloneOnly()
                || intent.installmentPreference() == InstallmentPreference.SEQUEL
                || intent.installmentPreference() == InstallmentPreference.ANY);
    }
    private boolean isEntryPoint(AniListMediaResponse media) {
        return media.relations() == null || media.relations().stream()
                .noneMatch(relation -> relation != null && "PREQUEL".equals(relation.relationType()));
    }
    private boolean isStandalone(AniListMediaResponse media) {
        return media.relations() == null || media.relations().stream()
                .noneMatch(relation -> relation != null && "SEQUEL".equals(relation.relationType()));
    }
    private boolean hasCentralRequiredGenres(AniListMediaResponse media, List<String> primaryGenres) {
        for (String genre : primaryGenres) {
            if (!"Romance".equalsIgnoreCase(genre)) continue;
            int evidence = media.tags().stream()
                    .filter(tag -> ROMANCE_THEME_TAGS.contains(tag.name().toLowerCase(Locale.ROOT)))
                    .mapToInt(RecommendationAssistantService::relevance).max().orElse(50);
            int penalty = 0;
            for (String candidateGenre : media.genres()) {
                penalty += ROMANCE_DISTRACTOR_PENALTIES.getOrDefault(candidateGenre.toLowerCase(Locale.ROOT), 0);
            }
            if (evidence - penalty < MIN_PRIMARY_GENRE_SCORE) return false;
        }
        return true;
    }
    private static boolean hasRelevantRequiredTags(List<MediaTagResponse> values, List<String> required) {
        return required.stream().allMatch(name -> values.stream().anyMatch(tag -> tag.name().equalsIgnoreCase(name)
                && relevance(tag) >= MIN_REQUIRED_TAG_RANK));
    }
    private static boolean hasRelevantExcludedTag(List<MediaTagResponse> values, List<String> excluded) {
        return excluded.stream().anyMatch(name -> values.stream().anyMatch(tag -> tag.name().equalsIgnoreCase(name)
                && relevance(tag) >= MIN_REQUIRED_TAG_RANK));
    }
    private static int relevance(MediaTagResponse tag) {
        return tag.rank() == null ? MIN_REQUIRED_TAG_RANK : Math.max(0, Math.min(100, tag.rank()));
    }
    private static boolean fits(Integer value, Integer min, Integer max) { return value == null ? min == null && max == null : (min == null || value >= min) && (max == null || value <= max); }
    private static boolean disjoint(List<String> values, List<String> excluded) { return excluded.stream().noneMatch(value -> containsIgnoreCase(values, value)); }
    private static boolean containsAll(List<String> values, List<String> required) { return required.stream().allMatch(value -> containsIgnoreCase(values, value)); }
    private static boolean containsIgnoreCase(List<String> values, String target) { return values.stream().anyMatch(value -> value.equalsIgnoreCase(target)); }
    private static List<String> split(String value) { return value == null || value.isBlank() ? List.of() : List.of(value.split("\\|")); }
    private static <T> List<T> safeValues(List<T> values) { return values == null ? List.of() : values; }
    private static <T> List<T> merge(List<T> first, List<T> second, int limit) { return java.util.stream.Stream.concat(first.stream(), second.stream()).distinct().limit(limit).toList(); }
    private static String upper(String value) { return value == null ? null : value.trim().toUpperCase(Locale.ROOT); }
    private static Integer clamp(Integer value, int min, int max) { return value == null ? null : Math.max(min, Math.min(max, value)); }
    private static Map<String, String> canonical(List<String> values) { Map<String, String> result = new LinkedHashMap<>(); values.forEach(value -> result.put(value.toLowerCase(Locale.ROOT), value)); return result; }
    private static List<String> valid(List<String> values, Map<String, String> allowed, int limit) { if (values == null) return List.of(); return values.stream().filter(java.util.Objects::nonNull).map(value -> allowed.get(value.toLowerCase(Locale.ROOT))).filter(java.util.Objects::nonNull).distinct().limit(limit).toList(); }
    private record CatalogSelection(List<AssistantMediaResponse> results, boolean referenceResolved) {}
}
