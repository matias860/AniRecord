package com.animelist.backend.service;

import com.animelist.backend.dto.assistant.RecommendationIntent;
import com.animelist.backend.enums.MediaType;
import com.animelist.backend.enums.ReleaseStatus;
import com.animelist.backend.enums.TrackingStatus;
import java.text.Normalizer;
import java.time.Year;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public class LocalRecommendationInterpreter {
    private static final Map<String, String> GENRES = new LinkedHashMap<>();
    static {
        GENRES.put("ciencia ficcion", "Sci-Fi"); GENRES.put("slice of life", "Slice of Life");
        GENRES.put("recuentos de la vida", "Slice of Life"); GENRES.put("mahou shoujo", "Mahou Shoujo");
        GENRES.put("magical girl", "Mahou Shoujo"); GENRES.put("accion", "Action");
        GENRES.put("aventura", "Adventure"); GENRES.put("comedia", "Comedy"); GENRES.put("drama", "Drama");
        GENRES.put("ecchi", "Ecchi"); GENRES.put("fantasia", "Fantasy"); GENRES.put("terror", "Horror");
        GENRES.put("horror", "Horror"); GENRES.put("mecha", "Mecha"); GENRES.put("musica", "Music");
        GENRES.put("misterio", "Mystery"); GENRES.put("psicologico", "Psychological");
        GENRES.put("romance", "Romance"); GENRES.put("deportes", "Sports");
        GENRES.put("sobrenatural", "Supernatural"); GENRES.put("suspenso", "Thriller");
        GENRES.put("thriller", "Thriller");
    }
    private static final Pattern YEAR = Pattern.compile("\\b(19[4-9]\\d|20\\d{2})\\b");
    private static final String UNIT_LABEL = "(?:episodios|capitulos)\\b";
    private static final Pattern UNIT_RANGE = Pattern.compile("(?:entre|de)\\s+(\\d{1,4})\\s+(?:y|a|hasta)\\s+(\\d{1,4})\\s*" + UNIT_LABEL);
    private static final Pattern UNIT_LESS_THAN = Pattern.compile("menos\\s+de\\s+(\\d{1,4})\\s*" + UNIT_LABEL);
    private static final Pattern UNIT_MORE_THAN = Pattern.compile("mas\\s+de\\s+(\\d{1,4})\\s*" + UNIT_LABEL);
    private static final Pattern UNIT_MAXIMUM = Pattern.compile("(?:maximo|hasta)\\s+(\\d{1,4})\\s*" + UNIT_LABEL);
    private static final Pattern UNIT_MINIMUM = Pattern.compile("(?:minimo|desde)\\s+(\\d{1,4})\\s*" + UNIT_LABEL);
    private static final Pattern UNIT_EXACT = Pattern.compile("\\b(\\d{1,4})\\s+(?:episodios|capitulos)\\b");
    private static final Pattern REFERENCE = Pattern.compile(
            "(?:parecid[oa]\\s+a|similar\\s+a|(?:algo|un anime|una serie|un manga|una pel[ií]cula)\\s+como)\\s+"
                    + "(?!para\\b|de\\b|que\\b)(.+?)(?:,|\\s+pero\\b|\\s+sin\\b|\\s+con\\b|$)",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    public ParseResult interpret(String text, RecommendationIntent previous, boolean strict) {
        return interpret(text, previous, strict, List.of());
    }

    public ParseResult interpret(String text, RecommendationIntent previous, boolean strict, List<String> allowedTags) {
        String value = normalize(text);
        boolean replacement = value.matches("^(?:no\\s*,?\\s*)?(?:ahora\\s+)?(?:quiero|busca|buscame|recomenda|recomendame)?\\s*(?:en cambio\\s+)?(?:un|una|anime|manga|pelicula).*" )
                || value.matches("^(?:cambia|cambialo|cambiala)\\s+(?:a|por)\\s+.*");
        boolean refinement = previous != null && !replacement
                && value.matches("^(otra|otro|pero|y |tambien|ademas|ahora|mas |menos |sin |con |hacela|hacelo|que sea).*");
        RecommendationIntent base = refinement ? previous : RecommendationIntent.empty();
        List<String> genres = new ArrayList<>(safe(base.genres()));
        List<String> tags = new ArrayList<>(safe(base.tags()));
        List<String> excluded = new ArrayList<>(safe(base.excludedGenres()));
        List<String> excludedTags = new ArrayList<>(safe(base.excludedTags()));
        int recognized = 0;
        for (var entry : GENRES.entrySet()) {
            if (!containsTerm(value, entry.getKey())) continue;
            if (isExcluded(value, entry.getKey())) {
                genres.remove(entry.getValue());
                add(excluded, entry.getValue());
            } else {
                excluded.remove(entry.getValue());
                add(genres, entry.getValue());
            }
            recognized++;
        }
        for (String allowedTag : allowedTags == null ? List.<String>of() : allowedTags) {
            if (allowedTag == null || allowedTag.isBlank()) continue;
            String normalizedTag = normalize(allowedTag);
            if (!containsTerm(value, normalizedTag)) continue;
            if (isExcluded(value, normalizedTag)) {
                removeIgnoreCase(tags, allowedTag);
                addIgnoreCase(excludedTags, allowedTag);
            } else {
                removeIgnoreCase(excludedTags, allowedTag);
                addIgnoreCase(tags, allowedTag);
            }
            recognized++;
        }
        boolean mentionsManga = value.contains("manga") || value.contains("manhwa") || value.contains("manhua");
        boolean mentionsAnime = value.contains("anime") || value.contains("serie") || value.contains("pelicula");
        MediaType type = mentionsAnime && mentionsManga ? null : mentionsManga ? MediaType.MANGA : mentionsAnime ? MediaType.ANIME : base.mediaType();
        if (mentionsAnime || mentionsManga) recognized++;
        String format = base.format();
        if (value.contains("pelicula") || value.contains("movie")) { format = "MOVIE"; recognized++; }
        else if (value.contains("one shot")) { format = "ONE_SHOT"; recognized++; }
        else if (value.contains("novela")) { format = "NOVEL"; type = MediaType.MANGA; recognized++; }
        ReleaseStatus status = base.status();
        if (value.contains("terminad") || value.contains("complet")) { status = ReleaseStatus.FINISHED; recognized++; }
        else if (value.contains("en emision")) { status = ReleaseStatus.RELEASING; recognized++; }
        else if (value.contains("proxim") || value.contains("todavia no sal")) { status = ReleaseStatus.NOT_YET_RELEASED; recognized++; }
        String country = base.country();
        if (value.contains("corean") || value.contains("manhwa")) { country = "KR"; recognized++; }
        else if (value.contains("chin") || value.contains("manhua")) { country = "CN"; recognized++; }
        else if (value.contains("japones")) { country = "JP"; recognized++; }
        String sort = base.sort();
        if (value.contains("mejor puntu") || value.contains("mejor valor")) { sort = "SCORE"; recognized++; }
        else if (value.contains("tendencia")) { sort = "TRENDING"; recognized++; }
        else if (value.contains("nuevo") || value.contains("reciente")) { sort = "NEWEST"; recognized++; }
        Integer min = base.minimumUnits(), max = base.maximumUnits();
        Matcher rangeMatch = UNIT_RANGE.matcher(value);
        Matcher lessThanMatch = UNIT_LESS_THAN.matcher(value);
        Matcher moreThanMatch = UNIT_MORE_THAN.matcher(value);
        Matcher maximumMatch = UNIT_MAXIMUM.matcher(value);
        Matcher minimumMatch = UNIT_MINIMUM.matcher(value);
        Matcher exactMatch = UNIT_EXACT.matcher(value);
        if (rangeMatch.find()) {
            min = Integer.valueOf(rangeMatch.group(1)); max = Integer.valueOf(rangeMatch.group(2));
            if (min > max) { int swap = min; min = max; max = swap; }
            recognized++;
        }
        else if (lessThanMatch.find()) { max = Math.max(1, Integer.parseInt(lessThanMatch.group(1)) - 1); recognized++; }
        else if (moreThanMatch.find()) { min = Math.min(10_000, Integer.parseInt(moreThanMatch.group(1)) + 1); recognized++; }
        else if (maximumMatch.find()) { max = Integer.valueOf(maximumMatch.group(1)); recognized++; }
        else if (minimumMatch.find()) { min = Integer.valueOf(minimumMatch.group(1)); recognized++; }
        else if (exactMatch.find()) { min = max = Integer.valueOf(exactMatch.group(1)); recognized++; }
        else if (value.contains("muy corto")) { max = 12; recognized++; }
        else if (value.contains("corto")) { max = 16; recognized++; }
        else if (value.contains("largo")) { min = 24; recognized++; }
        Integer yearFrom = base.yearFrom(), yearTo = base.yearTo();
        Matcher yearRange = Pattern.compile("\\b(19[4-9]\\d|20\\d{2})\\s*(?:a|y|hasta|-)\\s*(19[4-9]\\d|20\\d{2})\\b").matcher(value);
        Matcher yearMatch = YEAR.matcher(value);
        if (yearRange.find()) { yearFrom = Integer.valueOf(yearRange.group(1)); yearTo = Integer.valueOf(yearRange.group(2)); recognized++; }
        else if (yearMatch.find()) { yearFrom = yearTo = Integer.valueOf(yearMatch.group(1)); recognized++; }
        Matcher decade = Pattern.compile("(?:anos|decada)(?: de)?(?: los)?\\s*(19|20)(\\d)0").matcher(value);
        if (decade.find()) { yearFrom = Integer.valueOf(decade.group(1) + decade.group(2) + "0"); yearTo = yearFrom + 9; recognized++; }
        boolean catalogScope = value.matches(".*\\b(?:fuera de (?:mi )?(?:biblioteca|lista)|que no (?:este|esten) guardad[oa]s?|buscar? en (?:el )?catalogo|del catalogo)\\b.*");
        boolean libraryScope = !catalogScope && (value.contains("mi biblioteca") || value.contains("mi lista") || value.contains("pendiente"));
        boolean libraryScopeSpecified = catalogScope || libraryScope;
        boolean libraryOnly = catalogScope ? false : libraryScope ? true : base.libraryOnly();
        TrackingStatus libraryStatus = base.libraryStatus();
        if (catalogScope) { libraryStatus = null; recognized++; }
        else if (value.contains("pendiente")) { libraryStatus = TrackingStatus.PLANNED; recognized++; }
        String reference = null;
        Matcher ref = REFERENCE.matcher(text.trim());
        if (ref.find()) { reference = ref.group(1).trim(); recognized++; }
        if (refinement) recognized++;
        RecommendationIntent result = new RecommendationIntent(type, List.copyOf(genres), List.copyOf(tags), List.copyOf(excluded),
                List.copyOf(excludedTags), status, country, format, sort == null ? "POPULARITY" : sort,
                yearFrom, yearTo, base.season(), min, max, reference != null ? reference : base.referenceTitle(), libraryOnly, libraryStatus);
        return new ParseResult(result, !strict || recognized > 0 && !looksSubjective(value), libraryScopeSpecified);
    }

    private boolean looksSubjective(String value) {
        return value.matches(".*\\b(acogedor|emocionante|profundo|triste|divertido|oscuro|raro|sorprendente|animo)\\b.*");
    }
    private static boolean containsTerm(String value, String term) {
        return Pattern.compile("(?:^|[^\\p{L}\\p{N}])" + Pattern.quote(term) + "(?:$|[^\\p{L}\\p{N}])")
                .matcher(value).find();
    }
    private static boolean isExcluded(String value, String term) {
        String prefix = "(?:sin|no\\s+(?:quiero|busco|deseo)|(?:que\\s+)?no\\s+(?:tenga|incluya)|"
                + "evita|evitar|excluye|excluir|nada\\s+de|ni|tampoco)";
        String boundary = "(?:[,.;]|\\b(?:pero|aunque|en\\s+cambio|con|quiero|busco|que\\s+tenga)\\b)";
        return Pattern.compile("(?:^|[^\\p{L}\\p{N}])" + prefix + "\\s+"
                + "(?:(?!" + boundary + ").){0,120}?"
                + "(?<![\\p{L}\\p{N}])" + Pattern.quote(term) + "(?![\\p{L}\\p{N}])")
                .matcher(value).find();
    }
    private static String normalize(String value) { return Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT); }
    private static List<String> safe(List<String> value) { return value == null ? List.of() : List.copyOf(value); }
    private static void add(List<String> values, String value) { if (!values.contains(value)) values.add(value); }
    private static void addIgnoreCase(List<String> values, String value) { if (values.stream().noneMatch(item -> item.equalsIgnoreCase(value))) values.add(value); }
    private static void removeIgnoreCase(List<String> values, String value) { values.removeIf(item -> item.equalsIgnoreCase(value)); }
    public record ParseResult(RecommendationIntent intent, boolean complete, boolean libraryScopeSpecified) {}
}
