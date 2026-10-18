package com.animelist.backend.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.animelist.backend.entity.AppUser;
import com.animelist.backend.repository.AppUserRepository;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AssistantQuotaServiceTest {
    private static final ZoneId ARGENTINA = ZoneId.of("America/Argentina/Buenos_Aires");

    @Test
    void persistsLimitAndRefundsOnlyTodaysUsage() {
        AppUser user = new AppUser();
        user.setId(42L);
        AppUserRepository users = mock(AppUserRepository.class);
        when(users.findForAssistantQuotaById(42L)).thenReturn(Optional.of(user));
        when(users.findById(42L)).thenReturn(Optional.of(user));
        AssistantQuotaService quota = new AssistantQuotaService(2, users);

        assertTrue(quota.tryConsume(42L));
        assertTrue(quota.tryConsume(42L));
        assertFalse(quota.tryConsume(42L));
        assertEquals(LocalDate.now(ARGENTINA), user.getAssistantAiUsageDate());
        assertEquals(0, quota.remaining(42L));

        quota.refund(42L);
        assertEquals(1, quota.remaining(42L));
        assertTrue(quota.tryConsume(42L));
    }
}
