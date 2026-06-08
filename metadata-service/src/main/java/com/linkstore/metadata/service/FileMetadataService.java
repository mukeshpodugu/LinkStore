package com.linkstore.metadata.service;

import com.linkstore.metadata.dto.UploadInitRequest;
import com.linkstore.metadata.dto.UploadInitResponse;
import com.linkstore.metadata.dto.UploadInitResponse.ChunkUploadTask;
import com.linkstore.metadata.model.*;
import com.linkstore.metadata.repository.*;
import com.linkstore.metadata.service.sharding.ConsistentHashingRouter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class FileMetadataService {

    private final FileMetadataRepository fileRepository;
    private final FileVersionRepository versionRepository;
    private final FileChunkRepository chunkRepository;
    private final FolderRepository folderRepository;
    private final UserRepository userRepository;
    private final ConsistentHashingRouter shardingRouter;
    private final RedisTemplate<String, Object> redisTemplate;

    @Value("${storage.chunk-size-bytes}")
    private long chunkSize;

    @Value("${storage.replication-factor}")
    private int replicationFactor;

    public FileMetadataService(FileMetadataRepository fileRepository,
                               FileVersionRepository versionRepository,
                               FileChunkRepository chunkRepository,
                               FolderRepository folderRepository,
                               UserRepository userRepository,
                               ConsistentHashingRouter shardingRouter,
                               RedisTemplate<String, Object> redisTemplate) {
        this.fileRepository = fileRepository;
        this.versionRepository = versionRepository;
        this.chunkRepository = chunkRepository;
        this.folderRepository = folderRepository;
        this.userRepository = userRepository;
        this.shardingRouter = shardingRouter;
        this.redisTemplate = redisTemplate;
    }

    @Transactional
    public UploadInitResponse initUpload(UploadInitRequest request, UUID ownerId) {
        User owner = userRepository.findById(ownerId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Folder folder = null;
        if (request.getFolderId() != null) {
            folder = folderRepository.findById(request.getFolderId())
                    .orElseThrow(() -> new RuntimeException("Folder not found"));
        }

        // Deduplication Check
        Optional<FileMetadata> existingFile = fileRepository.findByHashSha256(request.getHashSha256());
        if (existingFile.isPresent()) {
            // Deduplication Hit: Store metadata, skip chunk upload
            FileMetadata duplicateFile = FileMetadata.builder()
                    .name(request.getName())
                    .extension(getFileExtension(request.getName()))
                    .sizeBytes(request.getSizeBytes())
                    .hashSha256(request.getHashSha256())
                    .folder(folder)
                    .owner(owner)
                    .isDeleted(false)
                    .build();
            
            fileRepository.save(duplicateFile);
            
            // Re-use current active version
            List<FileVersion> versions = versionRepository.findByFileIdOrderByVersionNumberDesc(existingFile.get().getId());
            if (!versions.isEmpty()) {
                FileVersion latestVersion = versions.get(0);
                FileVersion newVersion = FileVersion.builder()
                        .file(duplicateFile)
                        .versionNumber(1)
                        .sizeBytes(latestVersion.getSizeBytes())
                        .hashSha256(latestVersion.getHashSha256())
                        .createdBy(owner)
                        .build();
                versionRepository.save(newVersion);
                
                // Copy chunk references
                List<FileChunk> existingChunks = chunkRepository.findByFileVersionIdOrderByChunkIndexAsc(latestVersion.getId());
                for (FileChunk c : existingChunks) {
                    FileChunk newChunk = FileChunk.builder()
                            .fileVersion(newVersion)
                            .chunkIndex(c.getChunkIndex())
                            .sizeBytes(c.getSizeBytes())
                            .hashSha256(c.getHashSha256())
                            .primaryNodeId(c.getPrimaryNodeId())
                            .replicaNodeIds(c.getReplicaNodeIds())
                            .status("ACTIVE")
                            .build();
                    chunkRepository.save(newChunk);
                }
            }

            // Update user storage
            owner.setStorageUsedBytes(owner.getStorageUsedBytes() + request.getSizeBytes());
            userRepository.save(owner);

            incrementCacheCounter("hits");

            return UploadInitResponse.builder()
                    .fileId(duplicateFile.getId())
                    .isDeduplicated(true)
                    .chunks(Collections.emptyList())
                    .build();
        }

        incrementCacheCounter("misses");

        // Deduplication Miss: Generate sharding map
        FileMetadata newFile = FileMetadata.builder()
                .name(request.getName())
                .extension(getFileExtension(request.getName()))
                .sizeBytes(request.getSizeBytes())
                .hashSha256(request.getHashSha256())
                .folder(folder)
                .owner(owner)
                .isDeleted(false)
                .build();
        fileRepository.save(newFile);

        FileVersion firstVersion = FileVersion.builder()
                .file(newFile)
                .versionNumber(1)
                .sizeBytes(request.getSizeBytes())
                .hashSha256(request.getHashSha256())
                .createdBy(owner)
                .build();
        versionRepository.save(firstVersion);

        long totalSize = request.getSizeBytes();
        int chunkCount = (int) Math.ceil((double) totalSize / chunkSize);
        List<ChunkUploadTask> tasks = new ArrayList<>();

        for (int i = 0; i < chunkCount; i++) {
            long currentChunkSize = (i == chunkCount - 1) ? (totalSize - (i * chunkSize)) : chunkSize;
            String chunkHash = request.getHashSha256() + "_chunk_" + i;

            // Route using consistent hashing
            List<String> assignedNodes = shardingRouter.getNodes(chunkHash, replicationFactor);
            String primaryNode = assignedNodes.get(0);
            
            List<String> replicas = new ArrayList<>(assignedNodes);
            replicas.remove(primaryNode);
            String replicasStr = String.join(",", replicas);

            FileChunk chunk = FileChunk.builder()
                    .fileVersion(firstVersion)
                    .chunkIndex(i)
                    .sizeBytes(currentChunkSize)
                    .hashSha256(chunkHash)
                    .primaryNodeId(primaryNode)
                    .replicaNodeIds(replicasStr)
                    .status("ACTIVE")
                    .build();
            chunkRepository.save(chunk);

            // Construct node URLs (using port mappings)
            String primaryNodeUrl = getNodeUrl(primaryNode);
            List<String> replicaUrls = new ArrayList<>();
            for (String r : replicas) {
                replicaUrls.add(getNodeUrl(r));
            }

            tasks.add(ChunkUploadTask.builder()
                    .chunkIndex(i)
                    .sizeBytes(currentChunkSize)
                    .chunkHash(chunkHash)
                    .primaryNodeUrl(primaryNodeUrl)
                    .replicaNodeUrls(replicaUrls)
                    .build());
        }

        // Update user storage
        owner.setStorageUsedBytes(owner.getStorageUsedBytes() + request.getSizeBytes());
        userRepository.save(owner);

        return UploadInitResponse.builder()
                .fileId(newFile.getId())
                .isDeduplicated(false)
                .chunks(tasks)
                .build();
    }

    public Map<String, Object> getDownloadMap(UUID fileId, UUID userId) {
        FileMetadata file = fileRepository.findById(fileId)
                .orElseThrow(() -> new RuntimeException("File not found"));
        if (file.getIsDeleted()) {
            throw new RuntimeException("File is deleted");
        }
        
        // Cache read hit metric check
        incrementCacheCounter("hits");

        List<FileVersion> versions = versionRepository.findByFileIdOrderByVersionNumberDesc(fileId);
        if (versions.isEmpty()) {
            throw new RuntimeException("File versions missing");
        }
        FileVersion latest = versions.get(0);
        List<FileChunk> chunks = chunkRepository.findByFileVersionIdOrderByChunkIndexAsc(latest.getId());

        List<Map<String, Object>> chunkMaps = new ArrayList<>();
        for (FileChunk chunk : chunks) {
            Map<String, Object> cMap = new HashMap<>();
            cMap.put("chunkIndex", chunk.getChunkIndex());
            cMap.put("sizeBytes", chunk.getSizeBytes());
            cMap.put("chunkHash", chunk.getHashSha256());
            cMap.put("primaryNodeUrl", getNodeUrl(chunk.getPrimaryNodeId()));
            
            List<String> replicaUrls = new ArrayList<>();
            if (!chunk.getReplicaNodeIds().isEmpty()) {
                for (String r : chunk.getReplicaNodeIds().split(",")) {
                    replicaUrls.add(getNodeUrl(r));
                }
            }
            cMap.put("replicaNodeUrls", replicaUrls);
            chunkMaps.add(cMap);
        }

        Map<String, Object> response = new HashMap<>();
        response.put("fileId", file.getId());
        response.put("fileName", file.getName());
        response.put("sizeBytes", file.getSizeBytes());
        response.put("mimeType", file.getMimeType());
        response.put("chunks", chunkMaps);

        return response;
    }

    @Transactional
    public void deleteFile(UUID fileId, UUID userId) {
        FileMetadata file = fileRepository.findById(fileId)
                .orElseThrow(() -> new RuntimeException("File not found"));
        if (!file.getOwner().getId().equals(userId)) {
            throw new RuntimeException("Unauthorized");
        }
        file.setIsDeleted(true);
        fileRepository.save(file);

        // Update quota usage
        User owner = file.getOwner();
        owner.setStorageUsedBytes(Math.max(0, owner.getStorageUsedBytes() - file.getSizeBytes()));
        userRepository.save(owner);
    }

    public List<FileMetadata> getFilesInFolder(UUID folderId, UUID userId) {
        return folderId == null 
                ? fileRepository.findByOwnerIdAndFolderIsNullAndIsDeletedFalse(userId)
                : fileRepository.findByOwnerIdAndFolderIdAndIsDeletedFalse(userId, folderId);
    }

    private String getFileExtension(String filename) {
        if (filename == null || !filename.contains(".")) return "";
        return filename.substring(filename.lastIndexOf(".") + 1);
    }

    private String getNodeUrl(String nodeId) {
        // Resolve internal hostname/port mappings
        // In local Docker, nodes are named linkstore-storage-1 etc.
        // But for outside client access we map ports
        switch (nodeId) {
            case "storage-node-1": return "http://localhost:8082";
            case "storage-node-2": return "http://localhost:8083";
            case "storage-node-3": return "http://localhost:8084";
            case "storage-node-4": return "http://localhost:8085";
            default: return "http://localhost:8082";
        }
    }

    private void incrementCacheCounter(String type) {
        try {
            redisTemplate.opsForValue().increment("linkstore:cache:" + type);
        } catch (Exception ignored) {}
    }
}
