package com.linkstore.metadata.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardUserResponse {
    private Long storageQuotaBytes;
    private Long storageUsedBytes;
    private Long storageRemainingBytes;
    private Long filesCount;
    private Long sharedLinksCount;
    private List<RecentActivity> recentActivities;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RecentActivity {
        private String action;
        private String details;
        private String timestamp;
    }
}
