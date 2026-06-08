package com.linkstore.metadata.controller;

import com.linkstore.metadata.config.HeaderAuthenticationFilter.UserPrincipal;
import com.linkstore.metadata.model.Folder;
import com.linkstore.metadata.service.FolderService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/folders")
public class FolderController {

    private final FolderService folderService;

    public FolderController(FolderService folderService) {
        this.folderService = folderService;
    }

    @PostMapping
    public ResponseEntity<Folder> createFolder(
            @RequestBody Map<String, String> request,
            @AuthenticationPrincipal UserPrincipal principal) {
        String name = request.get("name");
        String parentIdStr = request.get("parentId");
        UUID parentId = (parentIdStr != null && !parentIdStr.isEmpty()) ? UUID.fromString(parentIdStr) : null;

        Folder folder = folderService.createFolder(name, parentId, principal.getId());
        return ResponseEntity.ok(folder);
    }

    @GetMapping
    public ResponseEntity<List<Folder>> getRootFolders(@AuthenticationPrincipal UserPrincipal principal) {
        List<Folder> folders = folderService.getSubfolders(null, principal.getId());
        return ResponseEntity.ok(folders);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getFolderContents(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal) {
        Folder currentFolder = folderService.getFolder(id, principal.getId());
        List<Folder> subfolders = folderService.getSubfolders(id, principal.getId());
        
        Map<String, Object> response = new HashMap<>();
        response.put("current", currentFolder);
        response.put("subfolders", subfolders);
        
        return ResponseEntity.ok(response);
    }
}
