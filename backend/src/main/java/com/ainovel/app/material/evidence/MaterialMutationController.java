package com.ainovel.app.material.evidence;

import com.ainovel.app.common.CurrentUserResolver;
import com.ainovel.app.material.dto.FileImportJobDto;
import com.ainovel.app.material.dto.MaterialDto;

import jakarta.validation.Valid;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/v1/material-evidence/sources")
public class MaterialMutationController {
    private final MaterialMutationService mutations;
    private final CurrentUserResolver users;

    public MaterialMutationController(
            MaterialMutationService mutations, CurrentUserResolver users) {
        this.mutations = mutations;
        this.users = users;
    }

    @PostMapping
    public MaterialDto create(
            @AuthenticationPrincipal UserDetails user,
            @Valid @RequestBody MaterialMutationService.Create input) {
        return mutations.create(users.require(user), input);
    }

    @PostMapping(
            value = "/upload",
            consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    public FileImportJobDto upload(
            @AuthenticationPrincipal UserDetails user,
            @RequestPart("file") org.springframework.web.multipart.MultipartFile file,
            @RequestParam String requestKey)
            throws java.io.IOException {
        if (file.getSize() > 2_097_152)
            throw MaterialEvidenceService.error(
                    org.springframework.http.HttpStatus.PAYLOAD_TOO_LARGE,
                    "MATERIAL_UPLOAD_SIZE_LIMIT");
        String content;
        try {
            content =
                    java.nio.charset.StandardCharsets.UTF_8
                            .newDecoder()
                            .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                            .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
                            .decode(java.nio.ByteBuffer.wrap(file.getBytes()))
                            .toString();
        } catch (java.nio.charset.CharacterCodingException invalid) {
            throw MaterialEvidenceService.error(
                    org.springframework.http.HttpStatus.BAD_REQUEST,
                    "MATERIAL_UPLOAD_UTF8_REQUIRED");
        }
        return mutations.upload(
                users.require(user), file.getOriginalFilename(), content, requestKey);
    }

    @PutMapping("/{id}")
    public MaterialDto update(
            @AuthenticationPrincipal UserDetails user,
            @PathVariable UUID id,
            @Valid @RequestBody MaterialMutationService.Update input) {
        return mutations.update(users.require(user), id, input);
    }

    @PostMapping("/merge")
    public MaterialDto merge(
            @AuthenticationPrincipal UserDetails user,
            @Valid @RequestBody MaterialMutationService.Merge input) {
        return mutations.merge(users.require(user), input);
    }
}
