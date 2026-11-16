package com.ainovel.app.user;

import com.ainovel.app.common.CurrentUserResolver;
import com.ainovel.app.economy.EconomyService;
import com.ainovel.app.user.dto.UserIdentityResponse;
import com.ainovel.app.user.dto.UserSummaryResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class UserControllerTests {

    @Test
    void identityUsesAuthenticatedUserWithoutLoadingBalances() {
        var economy = mock(EconomyService.class);
        var users = mock(CurrentUserResolver.class);
        var controller = new UserController(economy, users, mock(UserSummaryQueryService.class));
        var principal = mock(UserDetails.class);
        var user = new User();
        user.setId(UUID.randomUUID());
        user.setUsername("author");
        user.setEmail("author@example.test");
        user.setRoles(Set.of("ROLE_ADMIN"));
        when(users.require(principal)).thenReturn(user);

        assertEquals(new UserIdentityResponse(user.getId(), "author", "author@example.test", null, "admin", false),
                controller.identity(principal));
        verifyNoInteractions(economy);
    }

    @Test
    void balanceRequestUsesAuthenticatedUserAndKeepsUnavailableAmountsNull() throws Exception {
        var economy = mock(EconomyService.class);
        var users = mock(CurrentUserResolver.class);
        var principal = mock(UserDetails.class);
        var user = new User();
        when(users.require(principal)).thenReturn(user);
        when(economy.creditBalance(user)).thenReturn(new EconomyService.CreditBalance(24L, null, null));
        var controller = new UserController(economy, users, mock(UserSummaryQueryService.class));

        var balance = new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(controller.creditBalance(principal));
        assertEquals(24L, balance.path("projectCredits").longValue());
        assertEquals(true, balance.path("publicCredits").isNull());
        assertEquals(true, balance.path("totalCredits").isNull());
    }

    @Test
    void retiredPasswordHintReturnsNotFound() throws Exception {
        var controller = new UserController(mock(EconomyService.class), mock(CurrentUserResolver.class), mock(UserSummaryQueryService.class));
        org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup(controller).build()
                .perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/v1/user/password"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isNotFound());
    }

    @Test
    void summaryShouldResolveCurrentUserAndDelegateToQueryService() {
        EconomyService economyService = mock(EconomyService.class);
        CurrentUserResolver currentUserResolver = mock(CurrentUserResolver.class);
        UserSummaryQueryService summaryQueryService = mock(UserSummaryQueryService.class);
        UserController controller = new UserController(economyService, currentUserResolver, summaryQueryService);
        UserDetails principal = mock(UserDetails.class);
        User user = new User();
        UserSummaryResponse summary = new UserSummaryResponse(4, 2, 100, 9);
        when(currentUserResolver.require(principal)).thenReturn(user);
        when(summaryQueryService.summary(user)).thenReturn(summary);

        ResponseEntity<UserSummaryResponse> response = controller.summary(principal);

        assertEquals(summary, response.getBody());
        verify(currentUserResolver).require(principal);
        verify(summaryQueryService).summary(user);
    }

}
