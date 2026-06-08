package com.linkstore.metadata.dto;

import lombok.Data;
import java.util.UUID;

@Data
public class ShareRequest {
    private UUID fileId;
    private UUID folderId;
    private String linkType; // PUBLIC, PRIVATE, PASSWORD_PROTECTED
    private String password;
    private Integer expirationHours;
}
