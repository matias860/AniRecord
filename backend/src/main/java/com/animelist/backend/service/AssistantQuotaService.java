package com.animelist.backend.service;

import java.time.LocalDate;
import java.time.ZoneId;
import com.animelist.backend.repository.AppUserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AssistantQuotaService {
    private static final ZoneId ARGENTINA = ZoneId.of("America/Argentina/Buenos_Aires");
    private final int dailyLimit;
    private final AppUserRepository users;

    public AssistantQuotaService(@Value("${app.assistant.ai-daily-limit:8}") int dailyLimit, AppUserRepository users) {
        this.dailyLimit = Math.max(0, dailyLimit);
        this.users = users;
    }

    @Transactional
    public boolean tryConsume(Long userId) {
        var user = users.findForAssistantQuotaById(userId).orElseThrow();
        LocalDate today = LocalDate.now(ARGENTINA);
        int used = today.equals(user.getAssistantAiUsageDate()) && user.getAssistantAiUsageCount() != null
                ? user.getAssistantAiUsageCount() : 0;
        if (used >= dailyLimit) return false;
        user.setAssistantAiUsageDate(today);
        user.setAssistantAiUsageCount(used + 1);
        return true;
    }

    @Transactional
    public void refund(Long userId) {
        var user = users.findForAssistantQuotaById(userId).orElseThrow();
        LocalDate today = LocalDate.now(ARGENTINA);
        if (today.equals(user.getAssistantAiUsageDate()) && user.getAssistantAiUsageCount() != null) {
            user.setAssistantAiUsageCount(Math.max(0, user.getAssistantAiUsageCount() - 1));
        }
    }

    @Transactional(readOnly = true)
    public int remaining(Long userId) {
        var user = users.findById(userId).orElseThrow();
        int used = LocalDate.now(ARGENTINA).equals(user.getAssistantAiUsageDate()) && user.getAssistantAiUsageCount() != null
                ? user.getAssistantAiUsageCount() : 0;
        return Math.max(0, dailyLimit - used);
    }
}
