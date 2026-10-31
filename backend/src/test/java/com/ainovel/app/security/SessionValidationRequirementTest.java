package com.ainovel.app.security;

import com.ainovel.app.security.remote.*;
import com.ainovel.app.integration.GrpcChannelFactory;
import com.ainovel.app.user.SsoUserProvisioningService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class SessionValidationRequirementTest {
    @Test void signedUserFilterCannotStartWithoutItsSessionValidator() {
        new ApplicationContextRunner().withUserConfiguration(JwtAuthFilter.class)
                .withBean(JwtService.class, () -> mock(JwtService.class))
                .withBean(SsoUserProvisioningService.class, () -> mock(SsoUserProvisioningService.class))
                .run(context -> assertThat(context).hasFailed());
    }
    @Test void theRetiredSwitchCannotDisableTheRealValidator() {
        new ApplicationContextRunner().withUserConfiguration(UserSessionValidator.class)
                .withPropertyValues("spring.profiles.active=local", "sso.session-validation.enabled=false")
                .withBean(ConsulUserGrpcEndpointResolver.class, () -> mock(ConsulUserGrpcEndpointResolver.class))
                .withBean(UserSessionValidationProperties.class, UserSessionValidationProperties::new)
                .withBean(GrpcChannelFactory.class, () -> mock(GrpcChannelFactory.class))
                .withBean(UserServiceJwtProvider.class, () -> mock(UserServiceJwtProvider.class))
                .run(context -> assertThat(context).hasSingleBean(UserSessionValidator.class));
    }
}
