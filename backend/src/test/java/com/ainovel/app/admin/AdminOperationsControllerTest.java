package com.ainovel.app.admin;

import com.ainovel.app.admin.ops.OpsRecordFileSink;
import com.ainovel.app.material.MaterialAdminService;
import com.ainovel.app.material.MaterialAdminReadRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.List;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AdminOperationsControllerTest {
    @Test
    void defaultsTo20AndForwardsPaginationSearchAndFilter() throws Exception {
        AdminOperationsQueryService service = mock(AdminOperationsQueryService.class);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new AdminOperationsController(mock(MaterialAdminService.class), service, mock(OpsRecordFileSink.class))).build();
        when(service.assets("stories", 0, 20, "")).thenReturn(new AdminOperationsQueryService.PageResult<>(List.of(), 0, 20, 0, 0));
        mvc.perform(get("/v1/admin/assets/stories")).andExpect(status().isOk()).andExpect(jsonPath("$.size").value(20)).andExpect(jsonPath("$.items").isArray());
        verify(service).assets("stories", 0, 20, "");
        when(service.assets("worlds", 2, 10, "A & B")).thenReturn(new AdminOperationsQueryService.PageResult<>(List.of(), 2, 10, 30, 3));
        mvc.perform(get("/v1/admin/assets/worlds").param("page", "2").param("size", "10").param("search", "A & B")).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(30));
        verify(service).assets("worlds", 2, 10, "A & B");
        when(service.assets("manuscripts", 0, 20, "")).thenReturn(new AdminOperationsQueryService.PageResult<>(List.of(), 0, 20, 0, 0));
        mvc.perform(get("/v1/admin/assets/manuscripts")).andExpect(status().isOk());
        verify(service).assets("manuscripts", 0, 20, "");
        when(service.qualityRuns(1, 20, "scene", "high")).thenReturn(new AdminOperationsQueryService.PageResult<>(List.of(), 1, 20, 22, 2));
        mvc.perform(get("/v1/admin/quality/runs").param("page", "1").param("search", "scene").param("filter", "high")).andExpect(status().isOk()).andExpect(jsonPath("$.totalPages").value(2));
        verify(service).qualityRuns(1, 20, "scene", "high");
        mvc.perform(get("/v1/admin/assets/stories").param("page", "invalid")).andExpect(status().isBadRequest());
    }

    @Test
    void pendingMaterialsUsePaginatedProjection() throws Exception {
        MaterialAdminService materials = mock(MaterialAdminService.class);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new AdminOperationsController(materials,
                mock(AdminOperationsQueryService.class), mock(OpsRecordFileSink.class))).build();
        when(materials.pending(2, 20)).thenReturn(new MaterialAdminReadRepository.Page(List.of(), 2, 20, 45, 3));
        mvc.perform(get("/v1/admin/materials/pending").param("page", "2"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(45))
                .andExpect(jsonPath("$.size").value(20));
        verify(materials).pending(2, 20);
    }
}
