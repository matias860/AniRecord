package com.animelist.backend.service;

import com.animelist.backend.dto.anilist.AniListMediaResponse;
import com.animelist.backend.dto.anilist.CatalogCategoriesResponse;
import com.animelist.backend.dto.anilist.CatalogTagResponse;
import com.animelist.backend.dto.assistant.AssistantMediaResponse;
import com.animelist.backend.dto.assistant.AssistantRecommendationResponse;
import com.animelist.backend.dto.assistant.RecommendationIntent;
import com.animelist.backend.dto.library.LibraryEntrySummaryResponse;
import com.animelist.backend.entity.AppUser;
import com.animelist.backend.enums.MediaType;
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
                            local.intent());
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
                    List.of(new CatalogTagResponse("Yuri", "", "Theme-Sexuality")));
        }
    }

    private RecommendationIntent mergeExplicitConstraints(RecommendationIntent interpreted, RecommendationIntent explicit) {
        if (interpreted == null) interpreted = RecommendationIntent.empty();
        List<String> excludedGenres = merge(safeValues(interpreted.excludedGenres()), safeValues(explicit.excludedGenres()), 10);
        List<String> excludedTags = merge(safeValues(interpreted.excludedTags()), safeValues(explicit.excludedTags()), 12);
        List<String> genres = merge(safeValues(interpreted.genres()), safeValues(explicit.genres()), 10).stream()
                .filter(value -> !containsIgnoreCase(excludedGenres, value)).toList();
        List<String> tags = merge(safeValues(interpreted.tags()), safeValues(explicit.tags()), 12).stream()
                .filter(value -> !containsIgnoreCase(excludedTags, value)).toList();
        return new RecommendationIntent(
                explicit.mediaType() != null ? explicit.mediaType() : interpreted.mediaType(),
                genres, tags, excludedGenres, excludedTags,
                explicit.status() != null ? explicit.status() : interpreted.status(),
                explicit.country() != null ? explicit.country() : interpreted.country(),
                explicit.format() != null ? explicit.format() : interpreted.format(),
                interpreted.sort(),
                explicit.yearFrom() != null ? explicit.yearFrom() : interpreted.yearFrom(),
                explicit.yearTo() != null ? explicit.yearTo() : interpreted.yearTo(),
                explicit.season() != null ? explicit.season() : interpreted.season(),
                explicit.minimumUnits() != null ? explicit.minimumUnits() : interpreted.minimumUnits(),
                explicit.maximumUnits() != null ? explicit.maximumUnits() : interpreted.maximumUnits(),
                explicit.referenceTitle() != null ? explicit.referenceTitle() : interpreted.referenceTitle(),
                explicit.libraryOnly() || interpreted.libraryOnly(),
                explicit.libraryStatus() != null ? explicit.libraryStatus() : interpreted.libraryStatus());
    }

    private CatalogSelection fromCatalog(RecommendationIntent intent, List<LibraryEntrySummaryResponse> library) {
        Set<Integer> known = library.stream().map(LibraryEntrySummaryResponse::anilistId).collect(java.util.stream.Collectors.toSet());
        AniListMediaResponse reference = findReference(intent, library);
        if (intent.referenceTitle() != null && reference == null) return new CatalogSelection(List.of(), false);
        List<String> referenceGenres = reference == null ? List.of() : reference.genres();
        List<String> referenceTags = reference == null ? List.of() : reference.tags().stream()
                .filter(tag -> tag.rank() == null || tag.rank() >= 60).map(tag -> tag.name()).toList();
        List<String> queryGenres = intent.genres().isEmpty() ? referenceGenres.stream().limit(2).toList() : intent.genres();
        List<String> queryTags = intent.tags();
        List<String> scoringGenres = merge(intent.genres(), referenceGenres, 8);
        List<String> scoringTags = merge(intent.tags(), referenceTags, 10);
        List<AniListMediaResponse> candidates = aniListClient.browseForAssistant(intent.mediaType(), 45,
                intent.yearFrom(), intent.yearTo(), intent.season(), queryGenres, queryTags, intent.status(),
                intent.country(), intent.format(), intent.sort()).results();
        Map<String, Integer> preferences = preferences(library);
        Integer referenceId = reference == null ? null : reference.anilistId();
        List<AssistantMediaResponse> results = candidates.stream()
                .filter(item -> !known.contains(item.anilistId()) && !item.anilistId().equals(referenceId))
                .filter(item -> fits(item.totalUnits(), intent.minimumUnits(), intent.maximumUnits()))
                .filter(item -> containsAll(item.genres(), intent.genres()))
                .filter(item -> containsAll(item.tags().stream().map(tag -> tag.name()).toList(), intent.tags()))
                .filter(item -> disjoint(item.genres(), intent.excludedGenres()))
                .filter(item -> disjoint(item.tags().stream().map(tag -> tag.name()).toList(), intent.excludedTags()))
                .sorted(Comparator.comparingInt((AniListMediaResponse item) -> score(item, scoringGenres, scoringTags, preferences)).reversed())
                .limit(6).map(item -> toResponse(item, null, reason(item, intent, reference))).toList();
        return new CatalogSelection(results, true);
    }

    private CatalogSelection fromLibrary(RecommendationIntent intent, List<LibraryEntrySummaryResponse> library) {
        AniListMediaResponse reference = findReference(intent, library);
        if (intent.referenceTitle() != null && reference == null) return new CatalogSelection(List.of(), false);
        List<AssistantMediaResponse> results = library.stream()
                .filter(item -> intent.libraryStatus() == null || item.trackingStatus() == intent.libraryStatus())
                .filter(item -> fits(item.totalUnits(), intent.minimumUnits(), intent.maximumUnits()))
                .filter(item -> containsAll(split(item.genres()), intent.genres()))
                .filter(item -> containsAll(split(item.tags()), intent.tags()))
                .filter(item -> disjoint(split(item.genres()), intent.excludedGenres()))
                .filter(item -> disjoint(split(item.tags()), intent.excludedTags()))
                .filter(item -> intent.status() == null || item.releaseStatus() == intent.status())
                .filter(item -> intent.format() == null || intent.format().equals(item.mediaFormat()))
                .filter(item -> intent.country() == null || intent.country().equals(item.countryOfOrigin()))
                .filter(item -> intent.season() == null || intent.season() == item.releaseSeason())
                .filter(item -> intent.yearFrom() == null || item.releaseYear() != null && item.releaseYear() >= intent.yearFrom())
                .filter(item -> intent.yearTo() == null || item.releaseYear() != null && item.releaseYear() <= intent.yearTo())
                .filter(item -> reference == null || !item.anilistId().equals(reference.anilistId()))
                .filter(item -> reference == null || sharesElements(item, reference))
                .sorted(Comparator.comparingInt((LibraryEntrySummaryResponse item) -> libraryScore(item, reference)).reversed())
                .limit(6).map(item -> new AssistantMediaResponse(item.anilistId(), item.mediaType(), item.mediaFormat(), item.title(),
                        item.romajiTitle(), item.coverImage(), item.totalUnits(), item.releaseStatus(), item.releaseYear(),
                        split(item.genres()), split(item.tags()), item.trackingStatus(), libraryReason(item, reference))).toList();
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
        MediaType type = raw.mediaType() == null && !raw.libraryOnly() ? MediaType.ANIME : raw.mediaType();
        Map<String, String> genres = canonical(categories.genres());
        Map<String, String> tags = canonical(categories.tags().stream().map(item -> item.name()).toList());
        int maximumYear = Year.now().getValue() + 3;
        Integer from = clamp(raw.yearFrom(), 1940, maximumYear);
        Integer to = clamp(raw.yearTo(), 1940, maximumYear);
        if (from != null && to != null && from > to) { int swap = from; from = to; to = swap; }
        String format = upper(raw.format());
        if (format == null || !(type == MediaType.ANIME ? ANIME_FORMATS : MANGA_FORMATS).contains(format)) format = null;
        String country = upper(raw.country());
        if (country == null || !COUNTRIES.contains(country)) country = null;
        String sort = upper(raw.sort());
        if (sort == null || !SORTS.contains(sort)) sort = "POPULARITY";
        Integer min = clamp(raw.minimumUnits(), 1, 10_000), max = clamp(raw.maximumUnits(), 1, 10_000);
        if (min != null && max != null && min > max) { int swap = min; min = max; max = swap; }
        String reference = raw.referenceTitle() == null ? null : raw.referenceTitle().trim();
        if (reference != null && reference.length() > 100) reference = reference.substring(0, 100);
        if (reference != null && reference.isBlank()) reference = null;
        return new RecommendationIntent(type, valid(raw.genres(), genres, 4), valid(raw.tags(), tags, 5),
                valid(raw.excludedGenres(), genres, 4), valid(raw.excludedTags(), tags, 5), raw.status(), country,
                format, sort, from, to, raw.season(), min, max, reference, raw.libraryOnly(), raw.libraryStatus());
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

    private int score(AniListMediaResponse item, List<String> genres, List<String> tags, Map<String, Integer> preferences) {
        int score = 0;
        for (String genre : item.genres()) {
            if (containsIgnoreCase(genres, genre)) score += 30;
            score += Math.min(8, preferences.getOrDefault(genre.toLowerCase(Locale.ROOT), 0));
        }
        for (var tag : item.tags()) {
            if (containsIgnoreCase(tags, tag.name())) score += 20;
            score += Math.min(5, preferences.getOrDefault(tag.name().toLowerCase(Locale.ROOT), 0));
        }
        return score;
    }

    private String reason(AniListMediaResponse item, RecommendationIntent intent, AniListMediaResponse reference) {
        List<String> facts = new ArrayList<>();
        List<String> matches = item.genres().stream().filter(genre -> containsIgnoreCase(intent.genres(), genre)).limit(2).toList();
        if (!matches.isEmpty()) facts.add("combina " + String.join(" y ", matches));
        List<String> matchingTags = item.tags().stream().map(tag -> tag.name())
                .filter(tag -> containsIgnoreCase(intent.tags(), tag)).limit(2).toList();
        if (!matchingTags.isEmpty()) facts.add("incluye " + String.join(" y ", matchingTags));
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
        return new AssistantMediaResponse(item.anilistId(), item.mediaType(), item.mediaFormat(), item.title(), item.romajiTitle(),
                item.coverImage(), item.totalUnits(), item.releaseStatus(), item.releaseYear(), item.genres(),
                item.tags().stream().map(tag -> tag.name()).toList(), libraryStatus, reason);
    }

    private List<String> understood(RecommendationIntent intent, boolean referenceApplied) {
        List<String> result = new ArrayList<>();
        if (intent.mediaType() != null) result.add(intent.mediaType() == MediaType.ANIME ? "Anime" : "Manga");
        result.addAll(intent.genres());
        result.addAll(intent.tags().stream().limit(2).toList());
        intent.excludedGenres().stream().limit(2).map(value -> "Sin " + value).forEach(result::add);
        intent.excludedTags().stream().limit(2).map(value -> "Sin " + value).forEach(result::add);
        if (intent.minimumUnits() != null) result.add("Desde " + intent.minimumUnits() + " unidades");
        if (intent.maximumUnits() != null) result.add("Hasta " + intent.maximumUnits() + " unidades");
        if (intent.status() == ReleaseStatus.FINISHED) result.add("Terminadas");
        if (intent.status() == ReleaseStatus.RELEASING) result.add("En emisión");
        if (intent.status() == ReleaseStatus.NOT_YET_RELEASED) result.add("Próximamente");
        if (intent.libraryOnly()) result.add("De tu biblioteca");
        if (referenceApplied && intent.referenceTitle() != null) result.add("Como " + intent.referenceTitle());
        return result.stream().distinct().limit(6).toList();
    }

    private boolean hasConcreteFilter(RecommendationIntent intent) {
        return intent.libraryOnly() || intent.referenceTitle() != null || !intent.genres().isEmpty() || !intent.tags().isEmpty()
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
    private static boolean fits(Integer value, Integer min, Integer max) { return value == null ? min == null && max == null : (min == null || value >= min) && (max == null || value <= max); }
    private static boolean disjoint(List<String> values, List<String> excluded) { return excluded.stream().noneMatch(value -> containsIgnoreCase(values, value)); }
    private static boolean containsAll(List<String> values, List<String> required) { return required.stream().allMatch(value -> containsIgnoreCase(values, value)); }
    private static boolean containsIgnoreCase(List<String> values, String target) { return values.stream().anyMatch(value -> value.equalsIgnoreCase(target)); }
    private static List<String> split(String value) { return value == null || value.isBlank() ? List.of() : List.of(value.split("\\|")); }
    private static List<String> safeValues(List<String> values) { return values == null ? List.of() : values; }
    private static List<String> merge(List<String> first, List<String> second, int limit) { return java.util.stream.Stream.concat(first.stream(), second.stream()).distinct().limit(limit).toList(); }
    private static String upper(String value) { return value == null ? null : value.trim().toUpperCase(Locale.ROOT); }
    private static Integer clamp(Integer value, int min, int max) { return value == null ? null : Math.max(min, Math.min(max, value)); }
    private static Map<String, String> canonical(List<String> values) { Map<String, String> result = new LinkedHashMap<>(); values.forEach(value -> result.put(value.toLowerCase(Locale.ROOT), value)); return result; }
    private static List<String> valid(List<String> values, Map<String, String> allowed, int limit) { if (values == null) return List.of(); return values.stream().filter(java.util.Objects::nonNull).map(value -> allowed.get(value.toLowerCase(Locale.ROOT))).filter(java.util.Objects::nonNull).distinct().limit(limit).toList(); }
    private record CatalogSelection(List<AssistantMediaResponse> results, boolean referenceResolved) {}
}
