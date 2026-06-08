package com.linkstore.metadata.service;

import com.linkstore.metadata.config.RabbitMQConfig;
import com.linkstore.metadata.dto.ShareRequest;
import com.linkstore.metadata.dto.ShareResponse;
import com.linkstore.metadata.model.FileMetadata;
import com.linkstore.metadata.model.Folder;
import com.linkstore.metadata.model.SharedLink;
import com.linkstore.metadata.model.User;
import com.linkstore.metadata.repository.FileMetadataRepository;
import com.linkstore.metadata.repository.FolderRepository;
import com.linkstore.metadata.repository.SharedLinkRepository;
import com.linkstore.metadata.repository.UserRepository;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class SharingService {

    private final SharedLinkRepository sharedLinkRepository;
    private final FileMetadataRepository fileRepository;
    private final FolderRepository folderRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final RabbitTemplate rabbitTemplate;

    public SharingService(SharedLinkRepository sharedLinkRepository,
                          FileMetadataRepository fileRepository,
                          FolderRepository folderRepository,
                          UserRepository userRepository,
                          PasswordEncoder passwordEncoder,
                          RabbitTemplate rabbitTemplate) {
        this.sharedLinkRepository = sharedLinkRepository;
        this.fileRepository = fileRepository;
        this.folderRepository = folderRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.rabbitTemplate = rabbitTemplate;
    }

    @Transactional
    public ShareResponse createShareLink(ShareRequest request, UUID creatorId) {
        User creator = userRepository.findById(creatorId)
                .orElseThrow(() -> new RuntimeException("Creator not found"));

        FileMetadata file = null;
        if (request.getFileId() != null) {
            file = fileRepository.findById(request.getFileId())
                    .orElseThrow(() -> new RuntimeException("File not found"));
            if (!file.getOwner().getId().equals(creatorId)) {
                throw new RuntimeException("Access denied");
            }
        }

        Folder folder = null;
        if (request.getFolderId() != null) {
            folder = folderRepository.findById(request.getFolderId())
                    .orElseThrow(() -> new RuntimeException("Folder not found"));
            if (!folder.getOwner().getId().equals(creatorId)) {
                throw new RuntimeException("Access denied");
            }
        }

        if (file == null && folder == null) {
            throw new RuntimeException("File or Folder ID must be specified");
        }

        String token = generateSecureToken();
        String passwordHash = null;
        if ("PASSWORD_PROTECTED".equalsIgnoreCase(request.getLinkType()) && request.getPassword() != null) {
            passwordHash = passwordEncoder.encode(request.getPassword());
        }

        LocalDateTime expiresAt = null;
        if (request.getExpirationHours() != null && request.getExpirationHours() > 0) {
            expiresAt = LocalDateTime.now().plusHours(request.getExpirationHours());
        }

        SharedLink link = SharedLink.builder()
                .token(token)
                .file(file)
                .folder(folder)
                .creator(creator)
                .linkType(request.getLinkType().toUpperCase())
                .passwordHash(passwordHash)
                .expiresAt(expiresAt)
                .build();

        sharedLinkRepository.save(link);

        return mapToResponse(link);
    }

    public SharedLink getSharedLink(String token) {
        SharedLink link = sharedLinkRepository.findByToken(token)
                .orElseThrow(() -> new RuntimeException("Invalid shared link"));

        if (link.getExpiresAt() != null && link.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new RuntimeException("Shared link has expired");
        }

        return link;
    }

    public void verifyPassword(String token, String password) {
        SharedLink link = getSharedLink(token);
        if (!"PASSWORD_PROTECTED".equals(link.getLinkType())) {
            return;
        }

        if (link.getPasswordHash() == null || !passwordEncoder.matches(password, link.getPasswordHash())) {
            throw new RuntimeException("Incorrect password for shared link");
        }
    }

    // Asynchronously log the download event via RabbitMQ
    public void trackDownload(String token) {
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.EXCHANGE,
                RabbitMQConfig.DOWNLOAD_TRACKER_ROUTING_KEY,
                token
        );
    }

    private String generateSecureToken() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private ShareResponse mapToResponse(SharedLink link) {
        return ShareResponse.builder()
                .id(link.getId())
                .token(link.getToken())
                .downloadUrl("http://localhost:8080/api/v1/shares/public/" + link.getToken())
                .fileId(link.getFile() != null ? link.getFile().getId() : null)
                .fileName(link.getFile() != null ? link.getFile().getName() : link.getFolder().getName())
                .linkType(link.getLinkType())
                .expiresAt(link.getExpiresAt())
                .downloadCount(link.getDownloadCount())
                .createdAt(link.getCreatedAt())
                .build();
    }
}
