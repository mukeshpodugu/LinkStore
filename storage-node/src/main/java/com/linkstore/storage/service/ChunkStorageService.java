package com.linkstore.storage.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

@Service
public class ChunkStorageService {

    @Value("${storage.directory}")
    private String storageDirectory;

    @Value("${storage.node-id}")
    private String nodeId;

    private Path baseDirectory;

    @PostConstruct
    public void init() {
        baseDirectory = Paths.get(storageDirectory).toAbsolutePath().normalize();
        try {
            Files.createDirectories(baseDirectory);
            System.out.println("[" + nodeId + "] Initialized storage directory: " + baseDirectory);
        } catch (IOException e) {
            throw new RuntimeException("Failed to initialize storage directory", e);
        }
    }

    // Compress chunk bytes using GZIP and write to disk
    public void storeChunk(String chunkHash, byte[] rawBytes) throws IOException {
        // Enforce hash validation for integrity check
        String computedHash = calculateSha256(rawBytes);
        System.out.printf("[%s] Storing chunk %s. Size: %d bytes. Checksum: %s\n", 
                nodeId, chunkHash, rawBytes.length, computedHash);

        Path targetFile = baseDirectory.resolve(chunkHash + ".gz");
        
        try (FileOutputStream fos = new FileOutputStream(targetFile.toFile());
             GZIPOutputStream gzos = new GZIPOutputStream(fos)) {
            gzos.write(rawBytes);
            gzos.finish();
        }
    }

    // Read compressed chunk from disk and decompress
    public byte[] retrieveChunk(String chunkHash) throws IOException {
        Path sourceFile = baseDirectory.resolve(chunkHash + ".gz");
        if (!Files.exists(sourceFile)) {
            throw new FileNotFoundException("Chunk not found: " + chunkHash);
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (FileInputStream fis = new FileInputStream(sourceFile.toFile());
             GZIPInputStream gzis = new GZIPInputStream(fis)) {
            
            byte[] buffer = new byte[4096];
            int len;
            while ((len = gzis.read(buffer)) > 0) {
                baos.write(buffer, 0, len);
            }
        }
        
        byte[] decompressedBytes = baos.toByteArray();
        System.out.printf("[%s] Retrieved chunk %s. Extracted size: %d bytes\n", 
                nodeId, chunkHash, decompressedBytes.length);
        
        return decompressedBytes;
    }

    public void deleteChunk(String chunkHash) throws IOException {
        Path targetFile = baseDirectory.resolve(chunkHash + ".gz");
        if (Files.exists(targetFile)) {
            Files.delete(targetFile);
            System.out.printf("[%s] Deleted chunk %s\n", nodeId, chunkHash);
        }
    }

    private String calculateSha256(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data);
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }
}
