package com.animelist.backend.service;

import static org.junit.jupiter.api.Assertions.*;

import com.animelist.backend.dto.assistant.RecommendationIntent;
import com.animelist.backend.enums.MediaType;
import com.animelist.backend.enums.ReleaseStatus;
import com.animelist.backend.enums.InstallmentPreference;
import com.animelist.backend.enums.TrackingStatus;
import java.util.List;
import org.junit.jupiter.api.Test;

class LocalRecommendationInterpreterTest {
    private final LocalRecommendationInterpreter interpreter = new LocalRecommendationInterpreter();

    @Test
    void understandsAConcreteRequestWithoutAi() {
        var parsed = interpreter.interpret("Un romance terminado y corto, sin ecchi", null, true);
        assertTrue(parsed.complete());
        assertNull(parsed.intent().mediaType());
        assertEquals(ReleaseStatus.FINISHED, parsed.intent().status());
        assertEquals(16, parsed.intent().maximumUnits());
        assertEquals(java.util.List.of("Romance"), parsed.intent().genres());
        assertEquals(java.util.List.of("Ecchi"), parsed.intent().excludedGenres());
    }

    @Test
    void keepsPreviousFiltersWhenRefining() {
        RecommendationIntent previous = interpreter.interpret("Un manga de misterio", null, false).intent();
        var parsed = interpreter.interpret("pero corto y sin romance", previous, true).intent();
        assertEquals(MediaType.MANGA, parsed.mediaType());
        assertTrue(parsed.genres().contains("Mystery"));
        assertTrue(parsed.excludedGenres().contains("Romance"));
        assertFalse(parsed.genres().contains("Romance"));
        assertEquals(16, parsed.maximumUnits());
    }

    @Test
    void recognizesLibraryOnlyRequests() {
        var intent = interpreter.interpret("Algo corto de mis pendientes para hoy", null, false).intent();
        assertTrue(intent.libraryOnly());
        assertEquals(TrackingStatus.PLANNED, intent.libraryStatus());
    }

    @Test
    void startsFreshForANewCompleteRequest() {
        RecommendationIntent previous = interpreter.interpret("Un romance terminado y corto", null, false).intent();
        var next = interpreter.interpret("Una película de ciencia ficción", previous, false).intent();
        assertEquals(java.util.List.of("Sci-Fi"), next.genres());
        assertNull(next.status());
        assertNull(next.maximumUnits());
        assertEquals("MOVIE", next.format());
    }

    @Test
    void delegatesSubjectiveLanguageToAi() {
        assertFalse(interpreter.interpret("Quiero algo acogedor y sorprendente", null, true).complete());
    }

    @Test
    void understandsRangesMinimumsExactUnitsAndReferencesLocally() {
        var range = interpreter.interpret("Anime entre 2010 y 2020 con más de 24 episodios", null, true);
        assertTrue(range.complete());
        assertEquals(2010, range.intent().yearFrom());
        assertEquals(2020, range.intent().yearTo());
        assertEquals(25, range.intent().minimumUnits());
        var exact = interpreter.interpret("Un romance de 12 episodios", null, true).intent();
        assertEquals(12, exact.minimumUnits());
        assertEquals(12, exact.maximumUnits());
        var reference = interpreter.interpret("Parecido a Frieren", null, true);
        assertTrue(reference.complete());
        assertEquals("Frieren", reference.intent().referenceTitle());
    }

    @Test
    void understandsNaturalAndChainedNegations() {
        var direct = interpreter.interpret("No quiero romance", null, true).intent();
        assertEquals(List.of(), direct.genres());
        assertEquals(List.of("Romance"), direct.excludedGenres());

        var chained = interpreter.interpret("Quiero fantasía sin acción ni romance", null, true).intent();
        assertEquals(List.of("Fantasy"), chained.genres());
        assertTrue(chained.excludedGenres().containsAll(List.of("Action", "Romance")));

        var natural = interpreter.interpret("Algo que no tenga drama ni ecchi", null, true).intent();
        assertTrue(natural.excludedGenres().containsAll(List.of("Drama", "Ecchi")));
    }

    @Test
    void doesNotTreatComoParaAsAReferenceTitle() {
        var parsed = interpreter.interpret("Quiero algo corto como para ver hoy", null, true);
        assertTrue(parsed.complete());
        assertNull(parsed.intent().referenceTitle());
        assertEquals(16, parsed.intent().maximumUnits());
    }

    @Test
    void understandsUnitRangesAndStrictComparators() {
        var range = interpreter.interpret("Entre 12 y 24 episodios", null, true).intent();
        assertEquals(12, range.minimumUnits());
        assertEquals(24, range.maximumUnits());
        var moreThan = interpreter.interpret("Más de 24 episodios", null, true).intent();
        assertEquals(25, moreThan.minimumUnits());
        var lessThan = interpreter.interpret("Menos de 12 episodios", null, true).intent();
        assertEquals(11, lessThan.maximumUnits());
        var several = interpreter.interpret("Un manga yuri con varios capítulos", null, true, List.of("Yuri")).intent();
        assertEquals(20, several.minimumUnits());
    }

    @Test
    void canLeaveALibraryOnlyRefinement() {
        RecommendationIntent previous = interpreter.interpret("Algo de mis pendientes", null, false).intent();
        var corrected = interpreter.interpret("Ahora fuera de mi biblioteca, de acción", previous, true);

        assertFalse(corrected.intent().libraryOnly());
        assertNull(corrected.intent().libraryStatus());
        assertTrue(corrected.libraryScopeSpecified());
        assertEquals(List.of("Action"), corrected.intent().genres());
    }

    @Test
    void distinguishesReplacementFromNaturalRefinements() {
        RecommendationIntent previous = interpreter.interpret("Un romance terminado", null, false).intent();
        var replacement = interpreter.interpret("Ahora una película de ciencia ficción", previous, false).intent();
        assertEquals(java.util.List.of("Sci-Fi"), replacement.genres());
        assertNull(replacement.status());
        var refinement = interpreter.interpret("Hacela de 12 episodios", previous, false).intent();
        assertEquals(java.util.List.of("Romance"), refinement.genres());
        assertEquals(ReleaseStatus.FINISHED, refinement.status());
        assertEquals(12, refinement.maximumUnits());
    }

    @Test
    void libraryCanExplicitlyRequestBothMediaTypes() {
        var intent = interpreter.interpret("Anime y manga de mis pendientes", null, true).intent();
        assertNull(intent.mediaType());
        assertTrue(intent.libraryOnly());
    }

    @Test
    void understandsAniListTagsAndFlexibleNegations() {
        var parsed = interpreter.interpret(
                "Quiero un yuri en emisión, sin mucho drama y que sea largo",
                null, true, List.of("Yuri", "Female Protagonist"));

        assertTrue(parsed.complete());
        assertEquals(List.of("Yuri"), parsed.intent().tags());
        assertEquals(List.of("Drama"), parsed.intent().excludedGenres());
        assertFalse(parsed.intent().genres().contains("Drama"));
        assertEquals(ReleaseStatus.RELEASING, parsed.intent().status());
        assertEquals(24, parsed.intent().minimumUnits());
    }

    @Test
    void correctionStartingWithNoCreatesANewRequest() {
        RecommendationIntent previous = interpreter.interpret("Un drama largo en emisión", null, false).intent();
        var corrected = interpreter.interpret("No, quiero un yuri sin drama", previous, true, List.of("Yuri")).intent();

        assertEquals(List.of("Yuri"), corrected.tags());
        assertEquals(List.of("Drama"), corrected.excludedGenres());
        assertNull(corrected.status());
        assertNull(corrected.minimumUnits());
    }

    @Test
    void understandsPrimaryRomanceSpanishTagAndNoContinuation() {
        var parsed = interpreter.interpret(
                "Dame un romance de 12 capítulos sin segunda temporada, con triángulo amoroso",
                null, true, List.of("Love Triangle"));

        assertTrue(parsed.complete());
        assertEquals(List.of("Romance"), parsed.intent().primaryGenres());
        assertEquals(List.of("Love Triangle"), parsed.intent().tags());
        assertTrue(parsed.intent().standaloneOnly());
        assertEquals(12, parsed.intent().minimumUnits());
        assertEquals(12, parsed.intent().maximumUnits());
    }

    @Test
    void understandsNaturalVariationsWithoutDroppingRestrictions() {
        var parsed = interpreter.interpret(
                "Quiero un romance de doce capítulos, que no tenga segunda temporada y sin triángulos amorosos",
                null, true, List.of("Love Triangle"));

        assertTrue(parsed.complete());
        assertEquals(12, parsed.intent().minimumUnits());
        assertEquals(12, parsed.intent().maximumUnits());
        assertTrue(parsed.intent().standaloneOnly());
        assertEquals(List.of("Love Triangle"), parsed.intent().excludedTags());
    }

    @Test
    void appliesNegationToTypeCountryFormatAndStatus() {
        var status = interpreter.interpret("Quiero un manga de romance que no esté terminado", null, true);
        assertEquals(MediaType.MANGA, status.intent().mediaType());
        assertNull(status.intent().status());
        assertEquals(List.of(ReleaseStatus.FINISHED), status.intent().excludedStatuses());

        var country = interpreter.interpret("Manga no japonés", null, true);
        assertNull(country.intent().country());
        assertEquals(List.of("JP"), country.intent().excludedCountries());

        var type = interpreter.interpret("No quiero anime, quiero manga", null, true);
        assertEquals(MediaType.MANGA, type.intent().mediaType());
        assertEquals(List.of(MediaType.ANIME), type.intent().excludedMediaTypes());

        var format = interpreter.interpret("No quiero una película", null, true);
        assertNull(format.intent().format());
        assertEquals(List.of("MOVIE"), format.intent().excludedFormats());
    }

    @Test
    void explicitSecondSeasonChangesTheInstallmentPreference() {
        var parsed = interpreter.interpret("Recomendame una segunda temporada de acción", null, true);

        assertEquals(InstallmentPreference.SEQUEL, parsed.intent().installmentPreference());
        assertFalse(parsed.intent().standaloneOnly());
    }

    @Test
    void unknownNegativeRestrictionRequiresAiInterpretation() {
        var parsed = interpreter.interpret("Un romance sin violencia gráfica", null, true);

        assertFalse(parsed.complete());
        assertEquals(List.of("Romance"), parsed.intent().genres());
    }
}
