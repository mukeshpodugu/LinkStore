package com.linkstore.metadata.service.replication;

import com.linkstore.metadata.config.RabbitMQConfig;
import com.linkstore.metadata.model.AuditLog;
import com.linkstore.metadata.model.SharedLink;
import com.linkstore.metadata.repository.AuditLogRepository;
import com.linkstore.metadata.repository.SharedLinkRepository;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DownloadTrackerListener {

    private final SharedLinkRepository sharedLinkRepository;
    private final AuditLogRepository auditLogRepository;

    public DownloadTrackerListener(SharedLinkRepository sharedLinkRepository,
                                   AuditLogRepository auditLogRepository) {
        this.sharedLinkRepository = sharedLinkRepository;
        this.auditLogRepository = auditLogRepository;
    }

    @RabbitListener(queues = RabbitMQConfig.DOWNLOAD_TRACKER_QUEUE)
    @Transactional
    public void handleDownloadEvent(String token) {
        sharedLinkRepository.findByToken(token).ifPresent(link -> {
            // Update download count
            link.setDownloadCount(link.getDownloadCount() + 1);
            sharedLinkRepository.save(link);

            // Write audit log
            AuditLog auditLog = AuditLog.builder()
                    .user(link.getCreator()) // Shared by creator, downloaded by public
                    .action("SHARED_DOWNLOAD")
                    .details("File '" + (link.getFile() != null ? link.getFile().getName() : "Folder link") + 
                             "' downloaded via shared link. Current total: " + link.getDownloadCount())
                    .build();
            auditLogRepository.save(auditLog);
        });
    }
}
