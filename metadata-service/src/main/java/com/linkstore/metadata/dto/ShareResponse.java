package com.linkstore.metadata.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShareResponse {
    private UUID id;
    private String token;
    private String downloadUrl;
    private UUID fileId;
    private String fileName;
    private String linkType;
    private LocalDateTime expiresAt;
    private Integer downloadCount;
    private LocalDateTime createdAt;
}
