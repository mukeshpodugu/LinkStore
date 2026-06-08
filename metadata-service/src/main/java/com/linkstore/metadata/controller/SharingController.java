package com.linkstore.metadata.controller;

import com.linkstore.metadata.config.HeaderAuthenticationFilter.UserPrincipal;
import com.linkstore.metadata.dto.ShareRequest;
import com.linkstore.metadata.dto.ShareResponse;
import com.linkstore.metadata.model.FileMetadata;
import com.linkstore.metadata.model.SharedLink;
import com.linkstore.metadata.service.FileMetadataService;
import com.linkstore.metadata.service.SharingService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/shares")
public class SharingController {

    private final SharingService sharingService;
    private final FileMetadataService fileService;

    public SharingController(SharingService sharingService, FileMetadataService fileService) {
        this.sharingService = sharingService;
        this.fileService = fileService;
    }

    @PostMapping
    public ResponseEntity<ShareResponse> createShare(
            @RequestBody ShareRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        ShareResponse response = sharingService.createShareLink(request, principal.getId());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/public/{token}")
    public ResponseEntity<Map<String, Object>> getSharedDetails(@PathVariable String token) {
        SharedLink link = sharingService.getSharedLink(token);
        
        Map<String, Object> details = new HashMap<>();
        details.put("token", link.getToken());
        details.put("linkType", link.getLinkType());
        details.put("expiresAt", link.getExpiresAt());
        details.put("creator", link.getCreator().getUsername());
        
        if (link.getFile() != null) {
            details.put("itemType", "FILE");
            details.put("fileName", link.getFile().getName());
            details.put("sizeBytes", link.getFile().getSizeBytes());
        } else {
            details.put("itemType", "FOLDER");
            details.put("folderName", link.getFolder().getName());
        }

        return ResponseEntity.ok(details);
    }

    @PostMapping("/public/{token}/verify")
    public ResponseEntity<Map<String, String>> verifyPassword(
            @PathVariable String token,
            @RequestBody Map<String, String> request) {
        String password = request.get("password");
        try {
            sharingService.verifyPassword(token, password);
            Map<String, String> response = new HashMap<>();
            response.put("status", "SUCCESS");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, String> response = new HashMap<>();
            response.put("status", "FAILED");
            response.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
        }
    }

    @GetMapping("/public/{token}/download")
    public ResponseEntity<Map<String, Object>> getSharedDownloadMap(
            @PathVariable String token,
            @RequestParam(required = false) String password) {
        
        SharedLink link = sharingService.getSharedLink(token);
        if ("PASSWORD_PROTECTED".equals(link.getLinkType())) {
            sharingService.verifyPassword(token, password);
        }

        if (link.getFile() == null) {
            throw new RuntimeException("Cannot direct-download folder link");
        }

        // Increment download count and audit log
        sharingService.trackDownload(token);

        // Fetch download map for file chunks
        FileMetadata file = link.getFile();
        Map<String, Object> downloadMap = fileService.getDownloadMap(file.getId(), file.getOwner().getId());
        return ResponseEntity.ok(downloadMap);
    }
}
