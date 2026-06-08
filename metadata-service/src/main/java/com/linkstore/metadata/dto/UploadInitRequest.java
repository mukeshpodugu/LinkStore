package com.linkstore.metadata.dto;

import lombok.Data;
import java.util.UUID;

@Data
public class UploadInitRequest {
    private String name;
    private UUID folderId;
    private Long sizeBytes;
    private String hashSha256;
}
