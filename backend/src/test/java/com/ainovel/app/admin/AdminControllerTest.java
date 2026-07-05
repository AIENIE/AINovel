package com.ainovel.app.admin;

import com.ainovel.app.admin.dto.AdminDashboardStatsResponse;
import com.ainovel.app.admin.dto.AdminGrantCreditsRequest;
import com.ainovel.app.admin.dto.AdminSystemConfigResponse;
import com.ainovel.app.admin.dto.AdminSystemConfigUpdateRequest;
import com.ainovel.app.admin.dto.AdminUserDto;
import com.ainovel.app.admin.ops.OpsRecordFileSink;
import com.ainovel.app.economy.EconomyService;
import com.ainovel.app.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UserDetails;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminControllerTest {

    @Test
    void readEndpointsShouldDelegateToAdminConsoleService() {
        AdminConsoleService adminConsoleService = mock(AdminConsoleService.class);
        EconomyService economyService = mock(EconomyService.class);
        OpsRecordFileSink recordFileSink = mock(OpsRecordFileSink.class);
        AdminController controller = new AdminController(adminConsoleService, economyService, recordFileSink);

        AdminDashboardStatsResponse dashboard = new AdminDashboardStatsResponse(7L, 2L, 45.0, 12.0, 0.5, 3L);
        List<AdminUserDto> users = List.of(new AdminUserDto(
                "user-1", 1001L, "alice", "alice@example.com", "admin",
                700L, 40L, 740L, 2L, 1L, false,
                Instant.parse("2026-07-01T00:00:00Z"), Instant.parse("2026-07-02T00:00:00Z")
        ));
        AdminSystemConfigResponse config = new AdminSystemConfigResponse(false);
        when(adminConsoleService.dashboard()).thenReturn(dashboard);
        when(adminConsoleService.users("ali")).thenReturn(users);
        when(adminConsoleService.systemConfig()).thenReturn(config);

        ResponseEntity<com.ainovel.app.admin.dto.AdminDashboardStatsResponse> dashboardResponse = controller.dashboard();

        assertSame(dashboard, dashboardResponse.getBody());
        assertSame(users, controller.users("ali"));
        assertSame(config, controller.systemConfig());
    }

    @Test
    void updateSystemConfigShouldDelegateAndAudit() {
        AdminConsoleService adminConsoleService = mock(AdminConsoleService.class);
        OpsRecordFileSink recordFileSink = mock(OpsRecordFileSink.class);
        AdminController controller = new AdminController(adminConsoleService, mock(EconomyService.class), recordFileSink);
        UserDetails principal = mock(UserDetails.class);
        when(principal.getUsername()).thenReturn("root");
        AdminSystemConfigResponse response = new AdminSystemConfigResponse(true);
        when(adminConsoleService.updateSystemConfig(true)).thenReturn(response);

        AdminSystemConfigResponse actual = controller.updateSystemConfig(principal, new AdminSystemConfigUpdateRequest(true));

        assertSame(response, actual);
        verify(adminConsoleService).updateSystemConfig(true);
        verify(recordFileSink).appendAudit(argThat(fields ->
                "maintenance.update".equals(fields.get("action"))
                        && "root".equals(fields.get("actor"))
                        && "system-config".equals(fields.get("targetType"))
                        && "global".equals(fields.get("targetId"))
        ));
    }

    @Test
    void grantCreditsShouldResolveTargetUserThroughAdminConsoleService() {
        AdminConsoleService adminConsoleService = mock(AdminConsoleService.class);
        EconomyService economyService = mock(EconomyService.class);
        OpsRecordFileSink recordFileSink = mock(OpsRecordFileSink.class);
        AdminController controller = new AdminController(adminConsoleService, economyService, recordFileSink);
        UserDetails principal = mock(UserDetails.class);
        when(principal.getUsername()).thenReturn("root");
        User target = new User();
        target.setId(java.util.UUID.randomUUID());
        target.setUsername("alice");
        target.setEmail("alice@example.com");
        target.setPasswordHash("hash");
        target.setRemoteUid(1001L);
        target.setRoles(Set.of("ROLE_USER"));
        when(adminConsoleService.resolveTargetUser("1001")).thenReturn(target);
        when(economyService.grantProjectCredits(target, 100L, "活动补偿", "root"))
                .thenReturn(new EconomyService.CreditChangeResult(true, 100L, 600L, 50L, 650L, "OK"));

        var response = controller.grantCredits(principal, new AdminGrantCreditsRequest("1001", 100L, "活动补偿"));

        assertEquals(200, response.getStatusCode().value());
        assertEquals(600.0, response.getBody().projectCredits());
        assertEquals(650.0, response.getBody().totalCredits());
        verify(adminConsoleService).resolveTargetUser("1001");
        verify(recordFileSink).appendAudit(argThat(fields ->
                "credits.grant".equals(fields.get("action"))
                        && target.getId().toString().equals(fields.get("targetId"))
        ));
    }

    @Test
    void controllerShouldNotKeepRepositoryFields() {
        var fieldTypes = Arrays.stream(AdminController.class.getDeclaredFields())
                .map(Field::getType)
                .map(Class::getSimpleName)
                .toList();

        assertFalse(fieldTypes.stream().anyMatch(name -> name.endsWith("Repository")));
    }
}
