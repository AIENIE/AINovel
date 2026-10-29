package com.ainovel.app.modelconfig;

import com.ainovel.app.common.CurrentUserResolver;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/v2")
public class AiModelConfigurationController {
    private final AiModelConfigurationService config;
    private final AiQuoteService quotes;
    private final CurrentUserResolver users;
    public AiModelConfigurationController(AiModelConfigurationService config,AiQuoteService quotes,CurrentUserResolver users){this.config=config;this.quotes=quotes;this.users=users;}
    @GetMapping("/admin/model-catalog") @PreAuthorize("hasAuthority('AUTH_LOCAL_ADMIN')")
    public Map<String,Object> catalog(){return config.catalog();}
    @PostMapping("/admin/model-catalog/sync") @PreAuthorize("hasAuthority('AUTH_LOCAL_ADMIN')")
    public Map<String,Object> sync(){return config.sync();}
    @GetMapping("/ai-features")
    public List<AiModelConfigurationService.FeatureView> features(@AuthenticationPrincipal UserDetails principal){return config.features(users.require(principal));}
    @PostMapping("/ai-quotes")
    public AiQuoteService.View quote(@AuthenticationPrincipal UserDetails principal,@RequestBody AiQuoteService.Request request){return quotes.create(users.require(principal),request);}
    @GetMapping("/ai-quotes/pending")
    public List<AiQuoteService.Pending> pending(@AuthenticationPrincipal UserDetails principal){return quotes.pending(users.require(principal));}
}
