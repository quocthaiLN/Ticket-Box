package com.ticketbox.api.module.audit;

import com.ticketbox.api.infrastructure.security.JwtUtils;
import com.ticketbox.api.module.audit.domain.entities.AuditAction;
import com.ticketbox.api.module.audit.domain.entities.AuditLog;
import com.ticketbox.api.module.audit.repositories.AuditLogRepository;
import com.ticketbox.api.module.auth.domain.entities.User;
import com.ticketbox.api.module.auth.domain.entities.UserRole;
import com.ticketbox.api.module.auth.domain.entities.UserStatus;
import com.ticketbox.api.module.auth.repositories.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import jakarta.servlet.Filter;

import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
class AuditLogSecurityIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private Filter springSecurityFilterChain;

    private MockMvc mockMvc;

    @Autowired
    private JwtUtils jwtUtils;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    private User adminUser;
    private User audienceUser;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .addFilter(springSecurityFilterChain)
                .build();

        auditLogRepository.deleteAll();

        adminUser = userRepository.save(User.builder()
                .email("admin-" + UUID.randomUUID() + "@ticketbox.vn")
                .fullName("System Administrator")
                .role(UserRole.ADMIN)
                .status(UserStatus.ACTIVE)
                .build());

        audienceUser = userRepository.save(User.builder()
                .email("audience-" + UUID.randomUUID() + "@example.com")
                .fullName("Audience User")
                .role(UserRole.AUDIENCE)
                .status(UserStatus.ACTIVE)
                .build());
    }

    @Test
    void unauthenticatedRequest_toAuditLogs_returns401() throws Exception {
        mockMvc.perform(get("/admin/audit-logs")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void audienceRole_toAuditLogs_returns403_andAuditsAccessDenied() throws Exception {
        String token = jwtUtils.generateAccessToken(audienceUser.getId(), audienceUser.getRole().name());

        mockMvc.perform(get("/admin/audit-logs")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        // Verify that ACCESS_DENIED was recorded as an investigative audit log
        List<AuditLog> logs = auditLogRepository.findAll();
        assertFalse(logs.isEmpty(), "An ACCESS_DENIED audit log must be recorded");

        AuditLog accessDeniedLog = logs.stream()
                .filter(l -> l.getAction() == AuditAction.ACCESS_DENIED)
                .findFirst()
                .orElse(null);

        assertNotNull(accessDeniedLog, "Expected ACCESS_DENIED action in audit logs");
        assertEquals("SECURITY", accessDeniedLog.getEntityType());
        assertEquals("/admin/audit-logs", accessDeniedLog.getEntityId());
        assertEquals(audienceUser.getId(), accessDeniedLog.getActor().getId());
    }

    @Test
    void adminRole_toAuditLogs_returns200_andAuditsReadAction() throws Exception {
        String token = jwtUtils.generateAccessToken(adminUser.getId(), adminUser.getRole().name());

        mockMvc.perform(get("/admin/audit-logs")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.pagination").exists());

        // Auditing the Auditors: Verify that READ_AUDIT_LOGS was recorded
        List<AuditLog> logs = auditLogRepository.findAll();
        AuditLog readLog = logs.stream()
                .filter(l -> l.getAction() == AuditAction.READ_AUDIT_LOGS)
                .findFirst()
                .orElse(null);

        assertNotNull(readLog, "Expected READ_AUDIT_LOGS action in audit logs when admin queries");
        assertEquals("AUDIT_LOG", readLog.getEntityType());
        assertEquals("LIST", readLog.getEntityId());
        assertEquals(adminUser.getId(), readLog.getActor().getId());
    }

    @Test
    void auditLogsEndpoint_isImmutable_disallowsWriteMethods() throws Exception {
        String token = jwtUtils.generateAccessToken(adminUser.getId(), adminUser.getRole().name());

        // Attempt POST to /admin/audit-logs (must not be supported)
        mockMvc.perform(post("/admin/audit-logs")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"TEST\"}"))
                .andExpect(status().is(anyOf(is(405), is(404), is(403))));

        // Attempt PUT
        mockMvc.perform(put("/admin/audit-logs/" + UUID.randomUUID())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"TEST\"}"))
                .andExpect(status().is(anyOf(is(405), is(404), is(403))));

        // Attempt DELETE
        mockMvc.perform(delete("/admin/audit-logs/" + UUID.randomUUID())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().is(anyOf(is(405), is(404), is(403))));
    }
}
