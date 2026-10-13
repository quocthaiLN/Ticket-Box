package com.ticketbox.api.module.audit;

import com.ticketbox.api.module.audit.domain.entities.AuditAction;
import com.ticketbox.api.module.audit.domain.entities.AuditLog;
import com.ticketbox.api.module.audit.repositories.AuditLogRepository;
import com.ticketbox.api.module.audit.services.AuditLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class AuditLogTransactionIntegrationTest {

    @Autowired
    private AuditLogService auditLogService;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void cleanUp() {
        auditLogRepository.deleteAll();
    }

    @Test
    void logBusinessSuccess_rollsBack_whenOuterTransactionRollsBack() {
        TransactionTemplate template = new TransactionTemplate(transactionManager);

        assertThrows(RuntimeException.class, () -> {
            template.execute(status -> {
                auditLogService.logBusinessSuccess(
                        AuditAction.CREATE_CONCERT,
                        "CONCERT",
                        "crt-123",
                        Map.of("title", "Test Concert")
                );
                // Simulate exception causing business rollback
                throw new RuntimeException("Simulated business failure requiring rollback");
            });
        });

        // The audit log MUST be rolled back together with the business transaction
        assertEquals(0, auditLogRepository.count(),
                "Business success audit log must NOT exist when outer transaction rolls back");
    }

    @Test
    void logBusinessSuccess_commits_whenOuterTransactionCommits() {
        TransactionTemplate template = new TransactionTemplate(transactionManager);

        template.execute(status -> {
            auditLogService.logBusinessSuccess(
                    AuditAction.CREATE_CONCERT,
                    "CONCERT",
                    "crt-123",
                    Map.of("title", "Test Concert")
            );
            return null;
        });

        assertEquals(1, auditLogRepository.count());
        AuditLog saved = auditLogRepository.findAll().get(0);
        assertEquals(AuditAction.CREATE_CONCERT, saved.getAction());
        assertEquals("crt-123", saved.getEntityId());
    }

    @Test
    void logInvestigativeFailure_persists_evenWhenOuterTransactionRollsBack() {
        TransactionTemplate template = new TransactionTemplate(transactionManager);

        assertThrows(RuntimeException.class, () -> {
            template.execute(status -> {
                // Must be recorded in independent transaction (REQUIRES_NEW)
                auditLogService.logInvestigativeFailure(
                        AuditAction.LOGIN_FAILED,
                        "USER",
                        "victim@example.com",
                        Map.of("reason", "INVALID_PASSWORD")
                );
                // Simulate exception causing outer transaction rollback
                throw new RuntimeException("Simulated outer failure");
            });
        });

        // The investigative failure log MUST survive because it ran in REQUIRES_NEW
        assertEquals(1, auditLogRepository.count(),
                "Investigative failure audit log MUST persist even when outer transaction rolls back");

        AuditLog saved = auditLogRepository.findAll().get(0);
        assertEquals(AuditAction.LOGIN_FAILED, saved.getAction());
        assertEquals("victim@example.com", saved.getEntityId());
    }
}
