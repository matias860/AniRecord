package com.animelist.backend.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.animelist.backend.dto.anilist.AniListMediaResponse;
import com.animelist.backend.dto.anilist.AniListSearchResponse;
import com.animelist.backend.dto.anilist.CatalogCategoriesResponse;
import com.animelist.backend.dto.anilist.CatalogTagResponse;
import com.animelist.backend.dto.anilist.MediaTagResponse;
import com.animelist.backend.dto.assistant.RecommendationIntent;
import com.animelist.backend.dto.library.LibraryEntrySummaryResponse;
import com.animelist.backend.entity.AppUser;
import com.animelist.backend.enums.MediaType;
import com.animelist.backend.enums.ReleaseStatus;
import com.animelist.backend.enums.TrackingStatus;
import com.animelist.backend.repository.LibraryEntryRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RecommendationAssistantServiceTest {
    private CurrentUserService currentUser;
    private LibraryEntryRepository library;
    private AniListClient aniList;
    private GeminiRecommendationClient gemini;
    private AssistantQuotaService quota;
    private RecommendationAssistantService service;

    @BeforeEach
    void setup() {
        currentUser = mock(CurrentUserService.class); library = mock(LibraryEntryRepository.class);
        aniList = mock(AniListClient.class); gemini = mock(GeminiRecommendationClient.class);
        quota = mock(AssistantQuotaService.class);
        AppUser user = new AppUser(); user.setId(42L); when(currentUser.require()).thenReturn(user);
        when(aniList.getCategories()).thenReturn(new CatalogCategoriesResponse(
                List.of("Action", "Drama", "Ecchi", "Romance", "Sci-Fi"),
                List.of(new CatalogTagResponse("Yuri", "", "Theme-Sexuality"))));
        when(gemini.available()).thenReturn(false);
        when(quota.remaining(42L)).thenReturn(8);
        service = new RecommendationAssistantService(currentUser, library, aniList,
                new LocalRecommendationInterpreter(), gemini, quota);
    }

    @Test
    void concreteRequestUsesNoAiAndExcludesKnownLibraryTitles() {
        when(library.findSummaries(42L, null, null)).thenReturn(List.of(libraryItem(1, "Guardada")));
        when(aniList.browseForAssistant(isNull(), anyInt(), any(), any(), any(), anyList(), anyList(),
                eq(ReleaseStatus.FINISHED), any(), any(), eq("POPULARITY")))
                .thenReturn(new AniListSearchResponse(1, false, List.of(media(1, "Guardada"), media(2, "Nueva"))));
        var response = service.recommend("Un romance terminado y corto", null);
        assertEquals("LOCAL", response.source());
        assertEquals(List.of(2), response.results().stream().map(item -> item.anilistId()).toList());
        assertTrue(response.results().get(0).reason().contains("12 episodios"));
        verify(gemini, never()).interpret(anyString(), any(), anyList(), anyList());
    }

    @Test
    void libraryOnlyRequestDoesNotNeedCatalogSearch() {
        when(library.findSummaries(42L, null, null)).thenReturn(List.of(libraryItem(1, "Pendiente")));
        var response = service.recommend("Algo corto de mis pendientes para hoy", null);
        assertEquals(1, response.results().size());
        assertEquals(TrackingStatus.PLANNED, response.results().get(0).libraryStatus());
        verify(aniList, never()).browseForAssistant(any(), anyInt(), any(), any(), any(), anyList(), anyList(), any(), any(), any(), any());
    }

    @Test
    void busyGeminiDoesNotConsumeDailyQuota() {
        when(gemini.available()).thenReturn(true);
        when(quota.tryConsume(42L)).thenReturn(true);
        when(gemini.interpret(anyString(), any(), anyList(), anyList()))
                .thenThrow(new GeminiRecommendationClient.BusyException());

        var response = service.recommend("Quiero algo que me deje pensando durante días", null);

        assertEquals("FALLBACK", response.source());
        verify(quota).refund(42L);
    }

    @Test
    void emptyGeminiInterpretationDoesNotBecomeGenericPopularityResults() {
        when(gemini.available()).thenReturn(true);
        when(quota.tryConsume(42L)).thenReturn(true);
        when(gemini.interpret(anyString(), any(), anyList(), anyList())).thenReturn(RecommendationIntent.empty());

        var response = service.recommend("Quiero algo que me deje pensando durante días", null);

        assertEquals("FALLBACK", response.source());
        assertTrue(response.results().isEmpty());
        verify(aniList, never()).browseForAssistant(any(), anyInt(), any(), any(), any(), anyList(), anyList(), any(), any(), any(), any());
    }

    @Test
    void libraryComparisonExcludesReferenceItself() {
        LibraryEntrySummaryResponse referenceEntry = libraryItem(1, "Frieren");
        LibraryEntrySummaryResponse candidate = libraryItem(2, "Dungeon Meshi");
        when(library.findSummaries(42L, null, null)).thenReturn(List.of(referenceEntry, candidate));
        when(aniList.getMedia(1)).thenReturn(media(1, "Frieren"));

        var response = service.recommend("Algo de mi biblioteca parecido a Frieren", null);

        assertEquals(List.of(2), response.results().stream().map(item -> item.anilistId()).toList());
    }

    @Test
    void unknownReferenceIsNotReplacedByFirstSearchResult() {
        when(library.findSummaries(42L, MediaType.ANIME, null)).thenReturn(List.of());
        when(aniList.search("zzzz inexistente", MediaType.ANIME, 1, 5))
                .thenReturn(new AniListSearchResponse(1, false, List.of(media(9, "Frieren"))));

        var response = service.recommend("Algo parecido a zzzz inexistente", null);

        assertTrue(response.results().isEmpty());
        assertTrue(response.reply().contains("referencia"));
    }

    @Test
    void yuriRequestKeepsStatusLengthAndDramaExclusion() {
        AniListMediaResponse matching = new AniListMediaResponse(7, MediaType.ANIME, "TV", "JP", "Yuri largo", "Yuri largo",
                "cover", null, null, List.of(), null, 24, null, null, ReleaseStatus.RELEASING, null, 2026, 100, null,
                List.of(), List.of(), List.of(), List.of("Romance"), List.of(new MediaTagResponse("Yuri", 90, null, null)), 2026);
        when(library.findSummaries(42L, null, null)).thenReturn(List.of());
        when(aniList.browseForAssistant(isNull(), anyInt(), any(), any(), any(), anyList(), eq(List.of("Yuri")),
                eq(ReleaseStatus.RELEASING), any(), any(), eq("POPULARITY")))
                .thenReturn(new AniListSearchResponse(1, false, List.of(matching)));

        var response = service.recommend("Quiero un yuri en emisión, sin mucho drama y que sea largo", null);

        assertEquals("LOCAL", response.source());
        assertEquals(List.of(7), response.results().stream().map(item -> item.anilistId()).toList());
        assertTrue(response.intent().excludedGenres().contains("Drama"));
        assertTrue(response.understood().contains("Sin Drama"));
        assertTrue(response.understood().contains("En emisión"));
        assertTrue(response.results().get(0).reason().contains("Yuri tiene 90% de relevancia"));
        verify(gemini, never()).interpret(anyString(), any(), anyList(), anyList());
    }

    @Test
    void yuriUsesAniListRelevanceAndPenalizesThemesRelatedToDrama() {
        AniListMediaResponse incidentalYuri = candidate(20, "Yuri incidental", List.of("Psychological", "Horror", "Mystery"),
                List.of(new MediaTagResponse("Yuri", 35, null, null), new MediaTagResponse("Tragedy", 90, null, null)));
        AniListMediaResponse dramaticYuri = candidate(21, "Yuri dramático", List.of("Psychological", "Mystery"),
                List.of(new MediaTagResponse("Yuri", 90, null, null), new MediaTagResponse("Tragedy", 85, null, null)));
        AniListMediaResponse centralYuri = candidate(22, "Yuri tranquilo", List.of("Romance", "Slice of Life"),
                List.of(new MediaTagResponse("Yuri", 82, null, null)));
        when(library.findSummaries(42L, MediaType.MANGA, null)).thenReturn(List.of());
        when(aniList.browseForAssistant(eq(MediaType.MANGA), anyInt(), any(), any(), any(), anyList(), eq(List.of("Yuri")),
                any(), any(), any(), any())).thenReturn(new AniListSearchResponse(1, false,
                        List.of(incidentalYuri, dramaticYuri, centralYuri)));

        var response = service.recommend("Quiero un manga yuri con varios capítulos y sin mucho drama", null);

        assertEquals(List.of(22), response.results().stream().map(item -> item.anilistId()).toList());
        assertTrue(response.results().get(0).reason().contains("82% de relevancia"));
        assertEquals(20, response.intent().minimumUnits());
    }

    @Test
    void requestWithoutMediaTypeCombinesAnimeAndMangaWithoutClaimingAnimeWasRequested() {
        AniListMediaResponse anime = media(10, "Anime romántico");
        AniListMediaResponse manga = new AniListMediaResponse(11, MediaType.MANGA, "MANGA", "JP", "Manga romántico", "Manga romántico",
                "cover", null, null, List.of(), null, 10, null, null, ReleaseStatus.FINISHED, null, 2024, 200, null,
                List.of(), List.of(), List.of(), List.of("Romance"), List.of(), 2024);
        when(library.findSummaries(42L, null, null)).thenReturn(List.of());
        when(aniList.browseForAssistant(isNull(), anyInt(), any(), any(), any(), eq(List.of("Romance")), anyList(),
                any(), any(), any(), any())).thenReturn(new AniListSearchResponse(1, false, List.of(anime, manga)));

        var response = service.recommend("Quiero un romance corto", null);

        assertNull(response.intent().mediaType());
        assertFalse(response.understood().contains("Anime"));
        assertEquals(List.of(11, 10), response.results().stream().map(item -> item.anilistId()).toList());
    }

    @Test
    void explicitCatalogCorrectionOverridesAnAiAttemptToKeepLibraryScope() {
        RecommendationIntent previous = new RecommendationIntent(null, List.of(), List.of(), List.of(), List.of(),
                null, null, null, "POPULARITY", null, null, null, null, null, null, true, TrackingStatus.PLANNED);
        when(gemini.available()).thenReturn(true);
        when(quota.tryConsume(42L)).thenReturn(true);
        when(gemini.interpret(anyString(), any(), anyList(), anyList())).thenReturn(previous);
        when(library.findSummaries(42L, null, null)).thenReturn(List.of());
        when(aniList.browseForAssistant(any(), anyInt(), any(), any(), any(), anyList(), anyList(), any(), any(), any(), any()))
                .thenReturn(new AniListSearchResponse(1, false, List.of()));

        var response = service.recommend("Ahora fuera de mi biblioteca, quiero algo profundo de acción", previous);

        assertFalse(response.intent().libraryOnly());
        assertNull(response.intent().libraryStatus());
        assertTrue(response.intent().genres().contains("Action"));
    }

    @Test
    void explicitExclusionsRemainUsefulWhenSubjectiveLanguageCannotUseAi() {
        when(library.findSummaries(42L, null, null)).thenReturn(List.of());
        when(aniList.browseForAssistant(any(), anyInt(), any(), any(), any(), anyList(), anyList(), any(), any(), any(), any()))
                .thenReturn(new AniListSearchResponse(1, false, List.of()));

        var response = service.recommend("Algo divertido sin romance", null);

        assertEquals("FALLBACK", response.source());
        assertTrue(response.intent().excludedGenres().contains("Romance"));
        verify(aniList).browseForAssistant(isNull(), anyInt(), any(), any(), any(), anyList(), anyList(), any(), any(), any(), any());
    }

    private AniListMediaResponse media(int id, String title) {
        return new AniListMediaResponse(id, MediaType.ANIME, "TV", "JP", title, title, "cover", null, null,
                List.of(), null, 12, null, null, ReleaseStatus.FINISHED, null, 2024, 100, null,
                List.of(), List.of(), List.of(), List.of("Romance"), List.of(new MediaTagResponse("School", 70, null, null)), 2024);
    }

    private AniListMediaResponse candidate(int id, String title, List<String> genres, List<MediaTagResponse> tags) {
        return new AniListMediaResponse(id, MediaType.MANGA, "MANGA", "JP", title, title, "cover", null, null,
                List.of(), null, 40, null, null, ReleaseStatus.FINISHED, null, 2024, 100, null,
                List.of(), List.of(), List.of(), genres, tags, 2024);
    }

    private LibraryEntrySummaryResponse libraryItem(int id, String title) {
        return new LibraryEntrySummaryResponse((long) id, id, MediaType.ANIME, "TV", "JP", title, title, "cover",
                "Romance", "School", 12, ReleaseStatus.FINISHED, null, 2024, 0, null, 0,
                TrackingStatus.PLANNED, null, null, null, null);
    }
}
