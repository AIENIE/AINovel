package com.ainovel.app.world;

import com.ainovel.app.world.dto.WorldDefinitionDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/v1/world-building")
@Tag(name = "WorldDefinition", description = "世界观模块定义接口")
@SecurityRequirement(name = "bearerAuth")
public class WorldDefinitionController {
    @Autowired
    private WorldService worldService;

    @GetMapping("/definitions")
    @Operation(summary = "获取模块定义", description = "返回世界观模块、字段、类型与提示信息。")
    public List<WorldDefinitionDto> definitions() { return worldService.definitions(); }
}
