package com.animelist.backend.service;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.animelist.backend.enums.AccountType;
import com.animelist.backend.repository.LibraryEntryRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MediaRelationWatchServiceTest {
    @Mock LibraryEntryRepository libraryRepository;
    @Mock AniListClient aniListClient;
    @Mock MediaRelationPersistenceService persistenceService;

    @Test
    void scansOnlyMediaOwnedByLocalAccounts() {
        when(libraryRepository.findDistinctTrackedMediaByAccountType(AccountType.LOCAL)).thenReturn(List.of());
        var service = new MediaRelationWatchService(libraryRepository, aniListClient, persistenceService, true);

        service.scan();

        verify(libraryRepository).findDistinctTrackedMediaByAccountType(AccountType.LOCAL);
        verify(aniListClient, never()).getMediaRelations(List.of());
    }

    @Test
    void disabledWatcherDoesNotQueryTheDatabase() {
        var service = new MediaRelationWatchService(libraryRepository, aniListClient, persistenceService, false);

        service.scan();

        verify(libraryRepository, never()).findDistinctTrackedMediaByAccountType(AccountType.LOCAL);
    }
}
