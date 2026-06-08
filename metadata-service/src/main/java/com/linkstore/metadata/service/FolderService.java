package com.linkstore.metadata.service;

import com.linkstore.metadata.model.Folder;
import com.linkstore.metadata.model.User;
import com.linkstore.metadata.repository.FolderRepository;
import com.linkstore.metadata.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class FolderService {

    private final FolderRepository folderRepository;
    private final UserRepository userRepository;

    public FolderService(FolderRepository folderRepository, UserRepository userRepository) {
        this.folderRepository = folderRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public Folder createFolder(String name, UUID parentId, UUID ownerId) {
        User owner = userRepository.findById(ownerId)
                .orElseThrow(() -> new RuntimeException("Owner not found"));

        Folder parent = null;
        if (parentId != null) {
            parent = folderRepository.findById(parentId)
                    .orElseThrow(() -> new RuntimeException("Parent folder not found"));
            if (!parent.getOwner().getId().equals(ownerId)) {
                throw new RuntimeException("Unauthorized folder operation");
            }
        }

        // Check uniqueness
        boolean exists = parentId != null 
                ? folderRepository.findByNameAndParentIdAndOwnerId(name, parentId, ownerId).isPresent()
                : folderRepository.findByNameAndParentIsNullAndOwnerId(name, ownerId).isPresent();

        if (exists) {
            throw new RuntimeException("Folder with name '" + name + "' already exists in this directory");
        }

        Folder folder = Folder.builder()
                .name(name)
                .parent(parent)
                .owner(owner)
                .build();

        return folderRepository.save(folder);
    }

    public List<Folder> getSubfolders(UUID parentId, UUID ownerId) {
        return parentId == null 
                ? folderRepository.findByOwnerIdAndParentIsNull(ownerId)
                : folderRepository.findByOwnerIdAndParentId(ownerId, parentId);
    }

    public Folder getFolder(UUID id, UUID ownerId) {
        Folder folder = folderRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Folder not found"));
        if (!folder.getOwner().getId().equals(ownerId)) {
            throw new RuntimeException("Access denied");
        }
        return folder;
    }
}
