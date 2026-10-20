package com.animelist.backend.service;

import com.animelist.backend.dto.assistant.RecommendationIntent;
import com.animelist.backend.enums.InstallmentPreference;
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
    private static final Map<String, String> TAG_ALIASES = Map.ofEntries(
            Map.entry("triangulo amoroso", "Love Triangle"),
            Map.entry("triangulos amorosos", "Love Triangle"),
            Map.entry("amor no correspondido", "Unrequited Love"),
            Map.entry("amores no correspondidos", "Unrequited Love"),
            Map.entry("harem femenino", "Female Harem"),
            Map.entry("haremes femeninos", "Female Harem"),
            Map.entry("harem masculino", "Male Harem"),
            Map.entry("haremes masculinos", "Male Harem"));
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
    private static final String UNIT_LABEL = "(?:episodios?|capitulos?|caps?)\\b";
    private static final Pattern UNIT_RANGE = Pattern.compile("(?:entre|de)\\s+(\\d{1,4})\\s+(?:y|a|hasta)\\s+(\\d{1,4})\\s*" + UNIT_LABEL);
    private static final Pattern UNIT_LESS_THAN = Pattern.compile("menos\\s+de\\s+(\\d{1,4})\\s*" + UNIT_LABEL);
    private static final Pattern UNIT_MORE_THAN = Pattern.compile("mas\\s+de\\s+(\\d{1,4})\\s*" + UNIT_LABEL);
    private static final Pattern UNIT_MAXIMUM = Pattern.compile("(?:maximo|hasta)\\s+(\\d{1,4})\\s*" + UNIT_LABEL);
    private static final Pattern UNIT_MINIMUM = Pattern.compile("(?:minimo|desde)\\s+(\\d{1,4})\\s*" + UNIT_LABEL);
    private static final Pattern UNIT_EXACT = Pattern.compile("\\b(\\d{1,4})\\s+(?:episodios?|capitulos?|caps?)\\b");
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
        List<MediaType> excludedMediaTypes = new ArrayList<>(safe(base.excludedMediaTypes()));
        List<String> genres = new ArrayList<>(safe(base.genres()));
        List<String> primaryGenres = new ArrayList<>(safe(base.primaryGenres()));
        List<String> tags = new ArrayList<>(safe(base.tags()));
        List<String> excluded = new ArrayList<>(safe(base.excludedGenres()));
        List<String> excludedTags = new ArrayList<>(safe(base.excludedTags()));
        List<ReleaseStatus> excludedStatuses = new ArrayList<>(safe(base.excludedStatuses()));
        List<String> excludedCountries = new ArrayList<>(safe(base.excludedCountries()));
        List<String> excludedFormats = new ArrayList<>(safe(base.excludedFormats()));
        int recognized = 0;
        for (var entry : GENRES.entrySet()) {
            if (!containsTerm(value, entry.getKey())) continue;
            if (isExcluded(value, entry.getKey())) {
                genres.remove(entry.getValue());
                primaryGenres.remove(entry.getValue());
                add(excluded, entry.getValue());
            } else {
                excluded.remove(entry.getValue());
                add(genres, entry.getValue());
                if (!isSecondaryMention(value, entry.getKey())) add(primaryGenres, entry.getValue());
            }
            recognized++;
        }
        for (var alias : TAG_ALIASES.entrySet()) {
            if (!containsTerm(value, alias.getKey())) continue;
            String allowedTag = (allowedTags == null ? List.<String>of() : allowedTags).stream()
                    .filter(item -> item.equalsIgnoreCase(alias.getValue())).findFirst().orElse(null);
            if (allowedTag == null) continue;
            if (isExcluded(value, alias.getKey())) {
                removeIgnoreCase(tags, allowedTag);
                addIgnoreCase(excludedTags, allowedTag);
            } else {
                removeIgnoreCase(excludedTags, allowedTag);
                addIgnoreCase(tags, allowedTag);
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
        boolean mentionsManga = hasAnyPositiveTerm(value, "manga", "manhwa", "manwha", "manhua", "webtoon",
                "novela", "novela ligera", "novela liviana", "light novel", "ranobe");
        boolean mentionsAnime = hasPositiveTerm(value, "anime") || hasPositiveTerm(value, "serie")
                || hasPositiveTerm(value, "pelicula") || hasPositiveTerm(value, "donghua") || containsTerm(value, "temporada");
        boolean excludesManga = hasAnyExcludedTerm(value, "manga", "manhwa", "manwha", "manhua", "webtoon",
                "novela", "novela ligera", "novela liviana", "light novel", "ranobe");
        boolean excludesAnime = isMentionedAsExcluded(value, "anime") || isMentionedAsExcluded(value, "serie")
                || isMentionedAsExcluded(value, "pelicula") || isMentionedAsExcluded(value, "donghua");
        if (excludesManga) add(excludedMediaTypes, MediaType.MANGA); else if (mentionsManga) excludedMediaTypes.remove(MediaType.MANGA);
        if (excludesAnime) add(excludedMediaTypes, MediaType.ANIME); else if (mentionsAnime) excludedMediaTypes.remove(MediaType.ANIME);
        MediaType type = mentionsAnime && mentionsManga ? null : mentionsManga ? MediaType.MANGA
                : mentionsAnime ? MediaType.ANIME : base.mediaType();
        if (mentionsAnime || mentionsManga) recognized++;
        String format = base.format();
        if (isMentionedAsExcluded(value, "pelicula") || isMentionedAsExcluded(value, "movie")) {
            format = "MOVIE".equals(format) ? null : format; add(excludedFormats, "MOVIE"); recognized++;
        } else if (hasPositiveTerm(value, "pelicula") || hasPositiveTerm(value, "movie")) {
            format = "MOVIE"; excludedFormats.remove("MOVIE"); recognized++;
        }
        if (isMentionedAsExcluded(value, "one shot")) {
            format = "ONE_SHOT".equals(format) ? null : format; add(excludedFormats, "ONE_SHOT"); recognized++;
        } else if (hasPositiveTerm(value, "one shot")) {
            format = "ONE_SHOT"; excludedFormats.remove("ONE_SHOT"); recognized++;
        }
        boolean excludesNovel = hasAnyExcludedTerm(value, "novela", "novela ligera", "novela liviana", "light novel", "ranobe");
        boolean mentionsNovel = hasAnyPositiveTerm(value, "novela", "novela ligera", "novela liviana", "light novel", "ranobe");
        if (excludesNovel) {
            format = "NOVEL".equals(format) ? null : format; add(excludedFormats, "NOVEL"); recognized++;
        } else if (mentionsNovel) {
            format = "NOVEL"; excludedFormats.remove("NOVEL"); type = MediaType.MANGA; recognized++;
        }
        ReleaseStatus status = base.status();
        if (mentionsStem(value, "terminad", "complet")) {
            if (isExcludedStem(value, "terminad", "complet")) {
                if (status == ReleaseStatus.FINISHED) status = null;
                add(excludedStatuses, ReleaseStatus.FINISHED);
            } else {
                status = ReleaseStatus.FINISHED; excludedStatuses.remove(ReleaseStatus.FINISHED);
            }
            recognized++;
        } else if (value.contains("en emision")) { status = ReleaseStatus.RELEASING; excludedStatuses.remove(ReleaseStatus.RELEASING); recognized++; }
        else if (value.contains("proxim") || value.contains("todavia no sal")) { status = ReleaseStatus.NOT_YET_RELEASED; excludedStatuses.remove(ReleaseStatus.NOT_YET_RELEASED); recognized++; }
        String country = base.country();
        if (mentionsStem(value, "corean") || containsTerm(value, "manhwa") || containsTerm(value, "manwha")) {
            if (isExcludedStem(value, "corean") || hasAnyExcludedTerm(value, "manhwa", "manwha")) {
                if ("KR".equals(country)) country = null; add(excludedCountries, "KR");
            }
            else { country = "KR"; excludedCountries.remove("KR"); }
            recognized++;
        } else if (mentionsStem(value, "chin") || containsTerm(value, "manhua") || containsTerm(value, "donghua")) {
            if (isExcludedStem(value, "chin") || hasAnyExcludedTerm(value, "manhua", "donghua")) {
                if ("CN".equals(country)) country = null; add(excludedCountries, "CN");
            }
            else { country = "CN"; excludedCountries.remove("CN"); }
            recognized++;
        } else if (mentionsStem(value, "japones")) {
            if (isExcludedStem(value, "japones")) { if ("JP".equals(country)) country = null; add(excludedCountries, "JP"); }
            else { country = "JP"; excludedCountries.remove("JP"); }
            recognized++;
        }
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
        else if (value.matches(".*\\b(?:varios|muchos|bastantes)\\s+(?:episodios|capitulos)\\b.*")) { min = 20; recognized++; }
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
        boolean standaloneOnly = base.standaloneOnly();
        InstallmentPreference installmentPreference = base.installmentPreference() == null
                ? InstallmentPreference.FIRST : base.installmentPreference();
        boolean asksStandalone = value.matches(".*(?:sin (?:una )?(?:segunda|otra) temporada|que no (?:tenga|incluya) (?:una )?(?:segunda|otra) temporada|"
                + "sin (?:temporada\\s*[2-9]|continuacion(?:es)?|otra parte)|una sola temporada|autoconclusiv[oa]).*");
        boolean asksSequel = !asksStandalone && value.matches(".*(?:\\b(?:segunda|tercera|cuarta) temporada\\b|\\btemporada\\s*[2-9]\\b|\\bcontinuacion(?:es)?\\b).*");
        if (asksStandalone) {
            standaloneOnly = true;
            installmentPreference = InstallmentPreference.FIRST;
            recognized++;
        } else if (asksSequel) {
            standaloneOnly = false;
            installmentPreference = InstallmentPreference.SEQUEL;
            recognized++;
        } else if (value.matches(".*(?:cualquier temporada|no importa (?:la )?temporada|primera o segunda temporada).*")) {
            standaloneOnly = false;
            installmentPreference = InstallmentPreference.ANY;
            recognized++;
        }
        if (refinement) recognized++;
        RecommendationIntent result = new RecommendationIntent(type, List.copyOf(excludedMediaTypes),
                List.copyOf(genres), List.copyOf(primaryGenres),
                List.copyOf(tags), List.copyOf(excluded),
                List.copyOf(excludedTags), status, List.copyOf(excludedStatuses), country,
                List.copyOf(excludedCountries), format, List.copyOf(excludedFormats), sort == null ? "POPULARITY" : sort,
                yearFrom, yearTo, base.season(), min, max, reference != null ? reference : base.referenceTitle(),
                installmentPreference, standaloneOnly, libraryOnly, libraryStatus);
        boolean unresolvedRestriction = hasUnresolvedNegativeRestriction(value, allowedTags);
        return new ParseResult(result, !strict || recognized > 0 && !looksSubjective(value) && !unresolvedRestriction,
                libraryScopeSpecified);
    }

    private boolean looksSubjective(String value) {
        return value.matches(".*\\b(acogedor|emocionante|profundo|triste|divertido|oscuro|raro|sorprendente|animo)\\b.*");
    }
    private static boolean containsTerm(String value, String term) {
        return Pattern.compile("(?:^|[^\\p{L}\\p{N}])" + Pattern.quote(term) + "(?:$|[^\\p{L}\\p{N}])")
                .matcher(value).find();
    }
    private static boolean isExcluded(String value, String term) {
        String prefix = "(?:sin|no(?:\\s+(?:quiero|busco|deseo|tenga|incluya|sea|este))?|(?:que\\s+)?no\\s+(?:tenga|incluya|sea|este)|"
                + "evita|evitar|excluye|excluir|nada\\s+de|ni|tampoco)";
        String boundary = "(?:[,.;]|\\b(?:pero|aunque|en\\s+cambio|con|quiero|busco|que\\s+tenga)\\b)";
        return Pattern.compile("(?:^|[^\\p{L}\\p{N}])" + prefix + "\\s+(?:(?:un|una|el|la|los|las)\\s+)?"
                + "(?:(?!" + boundary + ").){0,120}?"
                + "(?<![\\p{L}\\p{N}])" + Pattern.quote(term) + "(?![\\p{L}\\p{N}])")
                .matcher(value).find();
    }
    private static boolean hasPositiveTerm(String value, String term) {
        return containsTerm(value, term) && !isExcluded(value, term);
    }
    private static boolean isMentionedAsExcluded(String value, String term) {
        return containsTerm(value, term) && isExcluded(value, term);
    }
    private static boolean hasAnyPositiveTerm(String value, String... terms) {
        for (String term : terms) if (hasPositiveTerm(value, term)) return true;
        return false;
    }
    private static boolean hasAnyExcludedTerm(String value, String... terms) {
        for (String term : terms) if (isMentionedAsExcluded(value, term)) return true;
        return false;
    }
    private static boolean mentionsStem(String value, String... stems) {
        for (String stem : stems) if (value.matches(".*\\b" + Pattern.quote(stem) + "[a-z]*\\b.*")) return true;
        return false;
    }
    private static boolean isExcludedStem(String value, String... stems) {
        for (String stem : stems) {
            Matcher matcher = Pattern.compile("\\b(" + Pattern.quote(stem) + "[a-z]*)\\b").matcher(value);
            while (matcher.find()) if (isExcluded(value, matcher.group(1))) return true;
        }
        return false;
    }
    private static boolean isSecondaryMention(String value, String term) {
        return Pattern.compile("(?:^|[^\\p{L}\\p{N}])con\\s+(?:algo\\s+de\\s+|un\\s+poco\\s+de\\s+)?"
                + Pattern.quote(term) + "(?:$|[^\\p{L}\\p{N}])").matcher(value).find();
    }
    private static boolean hasUnresolvedNegativeRestriction(String value, List<String> allowedTags) {
        Matcher matcher = Pattern.compile("(?:^|\\b)(?:sin|no(?:\\s+(?:quiero|busco|deseo|tenga|incluya|sea|este))?|"
                + "(?:que\\s+)?no\\s+(?:tenga|incluya|sea|este)|evita(?:r)?|exclu(?:ye|ir)|nada\\s+de|tampoco)\\s+"
                + "([^,.;]+?)(?=\\s+\\b(?:pero|aunque|en cambio|con|quiero|busco|que tenga|y que sea|y sin)\\b|$)").matcher(value);
        while (matcher.find()) {
            String remainder = matcher.group(1);
            for (String term : GENRES.keySet()) remainder = removeTerm(remainder, term);
            for (String term : TAG_ALIASES.keySet()) remainder = removeTerm(remainder, term);
            for (String term : allowedTags == null ? List.<String>of() : allowedTags) remainder = removeTerm(remainder, normalize(term));
            for (String term : List.of("anime", "manga", "manhwa", "manwha", "manhua", "webtoon", "donghua",
                    "serie", "pelicula", "movie", "one shot", "novela", "novela ligera", "novela liviana", "light novel", "ranobe",
                    "japones", "japonesa", "japoneses", "japonesas", "coreano", "coreana", "chino", "china",
                    "terminado", "terminada", "terminados", "terminadas", "completo", "completa", "completos", "completas",
                    "segunda temporada", "otra temporada", "temporada 2", "continuacion", "continuaciones", "otra parte")) {
                remainder = removeTerm(remainder, term);
            }
            remainder = remainder.replaceAll("\\b(?:un|una|el|la|los|las|mucho|mucha|muchos|muchas|algo|nada|de|del|"
                    + "ni|y|o|tambien|tampoco|que|sea|este|esta|tenga|incluya)\\b", " ")
                    .replaceAll("\\b\\d{1,4}\\s*(?:episodios?|capitulos?|caps?)\\b", " ")
                    .replaceAll("[^\\p{L}\\p{N}]+", " ").trim();
            if (remainder.matches(".*\\p{L}{3,}.*")) return true;
        }
        return false;
    }
    private static String removeTerm(String value, String term) {
        return value.replaceAll("(?<![\\p{L}\\p{N}])" + Pattern.quote(term) + "(?![\\p{L}\\p{N}])", " ");
    }
    private static String normalize(String value) {
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
        Map<String, Integer> numbers = Map.ofEntries(Map.entry("uno", 1), Map.entry("una", 1), Map.entry("dos", 2),
                Map.entry("tres", 3), Map.entry("cuatro", 4), Map.entry("cinco", 5), Map.entry("seis", 6),
                Map.entry("siete", 7), Map.entry("ocho", 8), Map.entry("nueve", 9), Map.entry("diez", 10),
                Map.entry("once", 11), Map.entry("doce", 12), Map.entry("trece", 13), Map.entry("catorce", 14),
                Map.entry("quince", 15), Map.entry("dieciseis", 16), Map.entry("diecisiete", 17),
                Map.entry("dieciocho", 18), Map.entry("diecinueve", 19), Map.entry("veinte", 20),
                Map.entry("veintiuno", 21), Map.entry("veintidos", 22), Map.entry("veintitres", 23),
                Map.entry("veinticuatro", 24), Map.entry("veinticinco", 25), Map.entry("veintiseis", 26),
                Map.entry("veintisiete", 27), Map.entry("veintiocho", 28), Map.entry("veintinueve", 29),
                Map.entry("treinta", 30));
        for (var number : numbers.entrySet()) {
            normalized = normalized.replaceAll("\\b" + number.getKey() + "(?=\\s+(?:episodios?|capitulos?|caps?)\\b)",
                    number.getValue().toString());
        }
        return normalized;
    }
    private static <T> List<T> safe(List<T> value) { return value == null ? List.of() : List.copyOf(value); }
    private static <T> void add(List<T> values, T value) { if (!values.contains(value)) values.add(value); }
    private static void addIgnoreCase(List<String> values, String value) { if (values.stream().noneMatch(item -> item.equalsIgnoreCase(value))) values.add(value); }
    private static void removeIgnoreCase(List<String> values, String value) { values.removeIf(item -> item.equalsIgnoreCase(value)); }
    public record ParseResult(RecommendationIntent intent, boolean complete, boolean libraryScopeSpecified) {}
}
