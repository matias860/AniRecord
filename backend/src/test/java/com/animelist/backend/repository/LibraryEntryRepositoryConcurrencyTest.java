package com.animelist.backend.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.animelist.backend.entity.AppUser;
import com.animelist.backend.entity.LibraryEntry;
import com.animelist.backend.entity.Media;
import com.animelist.backend.enums.AccountType;
import com.animelist.backend.enums.MediaType;
import com.animelist.backend.enums.TrackingStatus;
import java.math.BigDecimal;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:library_concurrency_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
class LibraryEntryRepositoryConcurrencyTest {
    @Autowired private LibraryEntryRepository entries;
    @Autowired private AppUserRepository users;
    @Autowired private MediaRepository mediaRepository;
    @Autowired private PlatformTransactionManager transactions;

    @Test
    void scoreEditWaitsForProgressIncrementAndPreservesIt() throws Exception {
        AppUser user = new AppUser();
        user.setAccountType(AccountType.LOCAL);
        user.setLocalUsername("concurrency-test");
        user.setDisplayName("Concurrency test");
        user = users.saveAndFlush(user);
        Media media = new Media();
        media.setAnilistId(987654);
        media.setMediaType(MediaType.ANIME);
        media.setTitle("Lock test");
        media.setTotalUnits(12);
        media = mediaRepository.saveAndFlush(media);
        LibraryEntry entry = new LibraryEntry();
        entry.setUser(user);
        entry.setMedia(media);
        entry.setProgress(5);
        entry.setTrackingStatus(TrackingStatus.IN_PROGRESS);
        entry = entries.saveAndFlush(entry);
        Long entryId = entry.getId();
        Long userId = user.getId();

        CountDownLatch incrementLocked = new CountDownLatch(1);
        CountDownLatch releaseIncrement = new CountDownLatch(1);
        CountDownLatch editStarted = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var increment = executor.submit(() -> new TransactionTemplate(transactions).executeWithoutResult(status -> {
                LibraryEntry locked = entries.findWithMediaForUpdateByIdAndUserId(entryId, userId).orElseThrow();
                incrementLocked.countDown();
                await(releaseIncrement);
                locked.setProgress(locked.getProgress() + 1);
                entries.save(locked);
            }));
            assertTrue(incrementLocked.await(2, TimeUnit.SECONDS));
            var scoreEdit = executor.submit(() -> new TransactionTemplate(transactions).executeWithoutResult(status -> {
                editStarted.countDown();
                LibraryEntry locked = entries.findWithMediaForUpdateByIdAndUserId(entryId, userId).orElseThrow();
                locked.setPersonalScore(new BigDecimal("8.0"));
                entries.save(locked);
            }));
            assertTrue(editStarted.await(2, TimeUnit.SECONDS));
            assertThrows(TimeoutException.class, () -> scoreEdit.get(150, TimeUnit.MILLISECONDS));

            releaseIncrement.countDown();
            increment.get(2, TimeUnit.SECONDS);
            scoreEdit.get(2, TimeUnit.SECONDS);
        } finally {
            releaseIncrement.countDown();
            executor.shutdownNow();
        }

        LibraryEntry stored = entries.findById(entryId).orElseThrow();
        assertEquals(6, stored.getProgress());
        assertEquals(new BigDecimal("8.0"), stored.getPersonalScore());
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(2, TimeUnit.SECONDS)) throw new IllegalStateException("Timeout waiting for test coordination");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(exception);
        }
    }
}
