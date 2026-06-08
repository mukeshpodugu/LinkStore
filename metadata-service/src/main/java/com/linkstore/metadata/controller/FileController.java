package com.linkstore.metadata.controller;

import com.linkstore.metadata.config.HeaderAuthenticationFilter.UserPrincipal;
import com.linkstore.metadata.dto.UploadInitRequest;
import com.linkstore.metadata.dto.UploadInitResponse;
import com.linkstore.metadata.model.FileMetadata;
import com.linkstore.metadata.service.FileMetadataService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/files")
public class FileController {

    private final FileMetadataService fileService;

    public FileController(FileMetadataService fileService) {
        this.fileService = fileService;
    }

    @PostMapping("/upload/init")
    public ResponseEntity<UploadInitResponse> initUpload(
            @RequestBody UploadInitRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        UploadInitResponse response = fileService.initUpload(request, principal.getId());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<Map<String, Object>> getDownloadMap(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal) {
        Map<String, Object> downloadMap = fileService.getDownloadMap(id, principal.getId());
        return ResponseEntity.ok(downloadMap);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteFile(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal) {
        fileService.deleteFile(id, principal.getId());
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public ResponseEntity<List<FileMetadata>> getFiles(
            @RequestParam(required = false) String folderId,
            @AuthenticationPrincipal UserPrincipal principal) {
        UUID fId = (folderId != null && !folderId.isEmpty()) ? UUID.fromString(folderId) : null;
        List<FileMetadata> files = fileService.getFilesInFolder(fId, principal.getId());
        return ResponseEntity.ok(files);
    }
}
