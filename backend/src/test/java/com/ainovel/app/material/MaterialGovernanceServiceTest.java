package com.ainovel.app.material;

import com.ainovel.app.common.ApiStatusException;
import com.ainovel.app.common.JsonColumnCodec;
import com.ainovel.app.manuscript.repo.ManuscriptRepository;
import com.ainovel.app.material.dto.MaterialUpdateRequest;
import com.ainovel.app.material.model.Material;
import com.ainovel.app.material.repo.MaterialRepository;
import com.ainovel.app.material.repo.MaterialUploadJobRepository;
import com.ainovel.app.security.ResourceAccessGuard;
import com.ainovel.app.user.User;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MaterialGovernanceServiceTest {
    MaterialRepository repository = mock(MaterialRepository.class);
    MaterialUploadJobRepository jobs = mock(MaterialUploadJobRepository.class);
    ResourceAccessGuard guard = mock(ResourceAccessGuard.class);
    MaterialFingerprintService fingerprints = mock(MaterialFingerprintService.class);
    ObjectMapper json = new ObjectMapper();
    MaterialService service = new MaterialService(repository,jobs,guard,mock(MaterialRetrievalService.class),
            mock(ManuscriptRepository.class),json,new JsonColumnCodec(json),fingerprints);

    @Test void ordinaryEditCannotApproveEvenWithGenericAdminRole() throws Exception {
        var owner = owner("other"); Material uploaded = material(owner,"UPLOAD","rejected");
        when(repository.findByIdForUpdate(uploaded.getId())).thenReturn(Optional.of(uploaded));
        when(guard.currentUsername()).thenReturn("generic-admin"); when(guard.isCurrentUserAdmin()).thenReturn(true);
        assertThrows(AccessDeniedException.class, () -> service.update(uploaded.getId(),new MaterialUpdateRequest(null,null,null,"new",null,null)));
        assertEquals("rejected",uploaded.getStatus()); verify(repository,never()).saveAndFlush(any());
        assertThrows(Exception.class, () -> json.readValue("{\"status\":\"approved\"}",MaterialUpdateRequest.class));
    }

    @Test void uploadEditInvalidatesReviewAndContentVersionButRejectedNeverSelfApproves() {
        var owner = owner("owner"); when(guard.currentUsername()).thenReturn("owner");
        var upload = material(owner,"UPLOAD","approved"); when(repository.findByIdForUpdate(upload.getId())).thenReturn(Optional.of(upload));
        service.update(upload.getId(),new MaterialUpdateRequest(null,null,null,"new",null,null));
        assertEquals("pending",upload.getStatus()); assertEquals(2,upload.getContentVersion()); verify(fingerprints).update(upload);
        var rejected = material(owner,"UPLOAD","rejected"); when(repository.findByIdForUpdate(rejected.getId())).thenReturn(Optional.of(rejected));
        service.update(rejected.getId(),new MaterialUpdateRequest(null,null,null,"new",null,null));
        assertEquals("rejected",rejected.getStatus()); assertEquals(2,rejected.getContentVersion());
    }

    @Test void orphanedUploadJobCannotBeReadOrAdvanceOnGet() {
        var owner = owner("owner");
        UserDetails details = org.springframework.security.core.userdetails.User.withUsername("owner").password("-").authorities("ROLE_USER").build();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(details,"-",List.of(new SimpleGrantedAuthority("ROLE_USER"))));
        when(guard.currentUser(details)).thenReturn(owner);
        try {
            var failure = assertThrows(ApiStatusException.class,() -> service.getUploadStatus(UUID.randomUUID()));
            assertEquals(404,failure.getStatus().value()); verify(jobs,never()).save(any());
        } finally { SecurityContextHolder.clearContext(); }
    }
    private static User owner(String name) { User u=new User();u.setId(UUID.randomUUID());u.setUsername(name);return u; }
    private static Material material(User owner,String source,String status) {
        Material m=new Material();m.setId(UUID.randomUUID());m.setUser(owner);m.setSource(source);m.setStatus(status);
        m.setContent("old");m.setTagsJson("[]");return m;
    }
}
