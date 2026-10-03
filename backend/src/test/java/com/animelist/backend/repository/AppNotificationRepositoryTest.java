package com.animelist.backend.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.animelist.backend.entity.AppNotification;
import com.animelist.backend.entity.AppUser;
import com.animelist.backend.enums.AccountType;
import com.animelist.backend.enums.NotificationType;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:notification_repository_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Transactional
class AppNotificationRepositoryTest {
    @Autowired private AppNotificationRepository repository;
    @Autowired private AppUserRepository userRepository;

    @Test
    void readsRecentNotificationsForOneUser() {
        AppUser user = new AppUser();
        user.setAccountType(AccountType.LOCAL);
        user.setLocalUsername("notification-test");
        user.setDisplayName("Notification test");
        user = userRepository.saveAndFlush(user);

        AppNotification notification = new AppNotification();
        notification.setUser(user);
        notification.setSourceKey("relation:test");
        notification.setNotificationType(NotificationType.RELATED_MEDIA_ADDITION);
        notification.setTitle("Nueva secuela");
        notification.setMessage("Se agregó una obra relacionada");
        notification.setRead(false);
        notification.setOccurredAt(LocalDateTime.now());
        repository.saveAndFlush(notification);

        var responses = repository.findResponsesByUserId(user.getId(), PageRequest.of(0, 10));

        assertThat(responses).singleElement().satisfies(response -> {
            assertThat(response.title()).isEqualTo("Nueva secuela");
            assertThat(response.read()).isFalse();
        });
    }
}
