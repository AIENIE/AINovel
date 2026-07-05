package com.ainovel.app.user;

import com.ainovel.app.common.CurrentUserResolver;
import com.ainovel.app.economy.EconomyService;
import com.ainovel.app.user.dto.UserSummaryResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UserDetails;

import java.lang.reflect.Field;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserControllerTests {

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

    @Test
    void controllerShouldNotKeepRepositoryFieldsForSummaryAggregation() {
        var fieldTypes = Arrays.stream(UserController.class.getDeclaredFields())
                .map(Field::getType)
                .map(Class::getSimpleName)
                .toList();

        assertFalse(fieldTypes.contains("StoryRepository"));
        assertFalse(fieldTypes.contains("WorldRepository"));
        assertFalse(fieldTypes.contains("OutlineRepository"));
        assertFalse(fieldTypes.contains("ManuscriptRepository"));
    }
}
