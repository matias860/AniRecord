package com.animelist.backend.service;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ThemeContrastTest {
    @Test void limitsBrightBackgroundsAndPreservesDarkColors() {
        assertEquals("#0a0c14", ThemeContrast.safeDarkColor("#0a0c14"));
        String safe = ThemeContrast.safeDarkColor("#ffffff");
        assertNotEquals("#ffffff", safe);
        assertEquals(safe, ThemeContrast.safeDarkColor(safe));
        assertEquals("#000000", ThemeContrast.safeDarkColor("#000000"));
    }
}
