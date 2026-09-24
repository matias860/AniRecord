package com.animelist.backend.service;

import com.animelist.backend.entity.Media;
import com.animelist.backend.entity.LibraryEntry;
import com.animelist.backend.enums.MediaType;
import com.animelist.backend.enums.TrackingStatus;
import java.time.LocalDate;
import java.util.List;

public final class TopNinePromptCatalog {
    private static final LocalDate FIRST_PERIOD = LocalDate.of(2026, 9, 16);
    private static final List<Prompt> PROMPTS = List.of(
            new Prompt("ANIME_QUE_TE_MARCARON", "9 animes que te marcaron", "Las historias que todavía forman parte de vos.", MediaType.ANIME, null, null),
            new Prompt("SUPERARON_EXPECTATIVAS", "9 obras que superaron tus expectativas", "Empezaron sin promesas y terminaron ganándose un lugar.", null, null, null),
            new Prompt("MEJORES_ROMANCES", "Tus 9 mejores romances", "Elegí entre las obras de Romance de tu biblioteca.", null, "Romance", null),
            new Prompt("FINALES_INOLVIDABLES", "9 finales que nunca olvidaste", "Buenos o dolorosos: los que todavía recordás.", null, null, null),
            new Prompt("MEJOR_ACCION", "Tus 9 mejores obras de acción", "Elegí entre las obras de Acción de tu biblioteca.", null, "Action", null),
            new Prompt("MANGAS_PARA_ANIMAR", "9 mangas que querés ver animados", "Las adaptaciones que más te gustaría recibir.", MediaType.MANGA, null, null),
            new Prompt("JOYAS_DESCONOCIDAS", "9 joyas que casi nadie conoce", "Tus hallazgos más personales.", null, null, null),
            new Prompt("TE_HICIERON_LLORAR", "9 obras que te hicieron llorar", "Las historias que consiguieron atravesarte.", null, null, null),
            new Prompt("MEJORES_COMEDIAS", "Tus 9 mejores comedias", "Elegí entre las comedias de tu biblioteca.", null, "Comedy", null),
            new Prompt("VOLVERIAS_A_EMPEZAR", "9 obras que volverías a empezar hoy", "Esas historias a las que siempre regresarías.", null, null, null),
            new Prompt("MEJOR_FANTASIA", "Tus 9 mejores fantasías", "Elegí entre las obras de Fantasía de tu biblioteca.", null, "Fantasy", null),
            new Prompt("DEFINIERON_TUS_GUSTOS", "9 obras que definieron tus gustos", "Una fotografía de cómo llegaste a ser el espectador que sos.", null, null, null),
            new Prompt("MEJORES_PELICULAS", "Tus 9 películas de anime favoritas", "Nueve historias completas en formato película.", MediaType.ANIME, null, "MOVIE"),
            new Prompt("TE_DEJARON_PENSANDO", "9 obras que te dejaron pensando", "Las que continuaron mucho después de terminarlas.", null, null, null),
            new Prompt("MEJOR_MISTERIO", "Tus 9 mejores misterios", "Elegí entre las obras de Misterio de tu biblioteca.", null, "Mystery", null),
            new Prompt("IMPERFECTAS_QUE_AMAS", "9 obras imperfectas que amás", "No necesitan ser perfectas para ser importantes.", null, null, null),
            new Prompt("MEJOR_CIENCIA_FICCION", "Tu mejor ciencia ficción en 9 obras", "Elegí entre las obras de Ciencia Ficción de tu biblioteca.", null, "Sci-Fi", null),
            new Prompt("NOSTALGIA", "9 obras que te generan nostalgia", "Las que te devuelven inmediatamente a otra época.", null, null, null),
            new Prompt("MEJOR_DRAMA", "Tus 9 mejores dramas", "Elegí entre los dramas de tu biblioteca.", null, "Drama", null),
            new Prompt("RECOMENDARIAS_SIN_EXPLICAR", "9 obras que recomendarías sin explicar", "Nueve portadas. Ninguna aclaración necesaria.", null, null, null),
            new Prompt("MEJOR_SLICE_OF_LIFE", "Tus 9 mejores slice of life", "Elegí entre las historias cotidianas de tu biblioteca.", null, "Slice of Life", null),
            new Prompt("VACIO_AL_TERMINAR", "9 obras que te dejaron un vacío", "Las despedidas que más costaron.", null, null, null),
            new Prompt("MEJOR_TERROR", "Tus 9 mejores obras de terror", "Elegí entre las obras de Terror de tu biblioteca.", null, "Horror", null),
            new Prompt("SIEMPRE_DEFENDERIAS", "9 obras que siempre defenderías", "Tus elecciones más firmes, aunque no todos coincidan.", null, null, null)
    );

    private TopNinePromptCatalog() {
    }

    public static Period current(LocalDate date) {
        LocalDate start = date.getDayOfMonth() <= 15 ? date.withDayOfMonth(1) : date.withDayOfMonth(16);
        LocalDate next = start.getDayOfMonth() == 1 ? start.withDayOfMonth(16) : start.plusMonths(1).withDayOfMonth(1);
        int serial = start.getYear() * 24 + (start.getMonthValue() - 1) * 2 + (start.getDayOfMonth() == 16 ? 1 : 0);
        int base = FIRST_PERIOD.getYear() * 24 + (FIRST_PERIOD.getMonthValue() - 1) * 2 + 1;
        Prompt prompt = PROMPTS.get(Math.floorMod(serial - base, PROMPTS.size()));
        return new Period(start, next, prompt);
    }

    public record Period(LocalDate startsAt, LocalDate changesAt, Prompt prompt) {
    }

    public record Prompt(String key, String title, String description, MediaType mediaType, String genre, String format) {
        public boolean requiresStarted() {
            return !"MANGAS_PARA_ANIMAR".equals(key);
        }

        public boolean matches(LibraryEntry entry) {
            return matches(entry.getMedia())
                    && (!requiresStarted() || entry.getTrackingStatus() != TrackingStatus.PLANNED);
        }

        public boolean matches(Media media) {
            if (mediaType != null && mediaType != media.getMediaType()) return false;
            if (format != null && (media.getMediaFormat() == null || !format.equalsIgnoreCase(media.getMediaFormat()))) return false;
            if (genre == null) return true;
            if (media.getGenres() == null) return false;
            return java.util.Arrays.stream(media.getGenres().split("\\|"))
                    .anyMatch(value -> genre.equalsIgnoreCase(value.trim()));
        }
    }
}
