package com.linkstore.storage.controller;

import com.linkstore.storage.service.ChunkStorageService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/api/v1/storage")
public class ChunkController {

    private final ChunkStorageService storageService;

    public ChunkController(ChunkStorageService storageService) {
        this.storageService = storageService;
    }

    @PostMapping(value = "/chunks", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<String> uploadChunk(
            @RequestParam("chunkHash") String chunkHash,
            @RequestParam("file") MultipartFile file) {
        try {
            byte[] bytes = file.getBytes();
            storageService.storeChunk(chunkHash, bytes);
            return ResponseEntity.ok("Chunk stored successfully");
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to store chunk: " + e.getMessage());
        }
    }

    @GetMapping("/chunks/{chunkHash}")
    public ResponseEntity<byte[]> downloadChunk(@PathVariable String chunkHash) {
        try {
            byte[] data = storageService.retrieveChunk(chunkHash);
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + chunkHash + "\"")
                    .body(data);
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
        }
    }

    @DeleteMapping("/chunks/{chunkHash}")
    public ResponseEntity<String> deleteChunk(@PathVariable String chunkHash) {
        try {
            storageService.deleteChunk(chunkHash);
            return ResponseEntity.ok("Chunk deleted successfully");
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to delete chunk: " + e.getMessage());
        }
    }
}
