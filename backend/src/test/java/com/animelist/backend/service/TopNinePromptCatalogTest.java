package com.animelist.backend.service;

import com.animelist.backend.entity.Media;
import com.animelist.backend.enums.MediaType;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TopNinePromptCatalogTest {
    @Test void changesPromptOnTheFirstAndSixteenthInArgentina() {
        var first = TopNinePromptCatalog.current(LocalDate.of(2026, 9, 23));
        var second = TopNinePromptCatalog.current(LocalDate.of(2026, 10, 1));

        assertEquals(LocalDate.of(2026, 9, 16), first.startsAt());
        assertEquals(LocalDate.of(2026, 10, 1), first.changesAt());
        assertEquals("ANIME_QUE_TE_MARCARON", first.prompt().key());
        assertEquals("SUPERARON_EXPECTATIVAS", second.prompt().key());
    }

    @Test void genreAndFormatPromptsOnlyAcceptMatchingLibraryMedia() {
        Media romance = media(MediaType.ANIME, "TV", "Action|Romance");
        Media actionMovie = media(MediaType.ANIME, "MOVIE", "Action");
        var romancePrompt = TopNinePromptCatalog.current(LocalDate.of(2026, 10, 16)).prompt();
        var moviePrompt = TopNinePromptCatalog.current(LocalDate.of(2027, 3, 16)).prompt();

        assertTrue(romancePrompt.matches(romance));
        assertFalse(romancePrompt.matches(actionMovie));
        assertTrue(moviePrompt.matches(actionMovie));
        assertFalse(moviePrompt.matches(romance));
    }

    private Media media(MediaType type, String format, String genres) {
        Media media = new Media();
        media.setMediaType(type);
        media.setMediaFormat(format);
        media.setGenres(genres);
        return media;
    }
}
