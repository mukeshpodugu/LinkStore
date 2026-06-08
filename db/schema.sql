-- PostgreSQL Database Schema for LinkStore

-- Enable pgcrypto for UUID generation if needed, though gen_random_uuid() is built-in for PG 13+
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- 1. Users Table
CREATE TABLE IF NOT EXISTS users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    username VARCHAR(50) UNIQUE NOT NULL,
    email VARCHAR(100) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(20) DEFAULT 'USER' NOT NULL, -- ADMIN, USER
    storage_quota_bytes BIGINT DEFAULT 5368709120 NOT NULL, -- Default 5GB
    storage_used_bytes BIGINT DEFAULT 0 NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 2. Folders Table (Adjacency List Model for Hierarchy)
CREATE TABLE IF NOT EXISTS folders (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL,
    parent_id UUID REFERENCES folders(id) ON DELETE CASCADE,
    owner_id UUID REFERENCES users(id) ON DELETE CASCADE NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT unique_folder_per_parent UNIQUE (name, parent_id, owner_id)
);

-- 3. Files Table (Logical File Metadata)
CREATE TABLE IF NOT EXISTS files (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL,
    extension VARCHAR(50),
    mime_type VARCHAR(100),
    size_bytes BIGINT NOT NULL,
    hash_sha256 VARCHAR(64) NOT NULL, -- Used for deduplication
    folder_id UUID REFERENCES folders(id) ON DELETE CASCADE,
    owner_id UUID REFERENCES users(id) ON DELETE CASCADE NOT NULL,
    is_deleted BOOLEAN DEFAULT FALSE NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 4. File Versions Table
CREATE TABLE IF NOT EXISTS file_versions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    file_id UUID REFERENCES files(id) ON DELETE CASCADE NOT NULL,
    version_number INT NOT NULL,
    size_bytes BIGINT NOT NULL,
    hash_sha256 VARCHAR(64) NOT NULL,
    created_by UUID REFERENCES users(id) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT unique_version_per_file UNIQUE (file_id, version_number)
);

-- 5. Chunks Table (Maps Logical Files/Versions to Physical Storage Nodes)
CREATE TABLE IF NOT EXISTS file_chunks (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    file_version_id UUID REFERENCES file_versions(id) ON DELETE CASCADE NOT NULL,
    chunk_index INT NOT NULL, -- 0, 1, 2...
    size_bytes BIGINT NOT NULL,
    hash_sha256 VARCHAR(64) NOT NULL,
    primary_node_id VARCHAR(50) NOT NULL, -- e.g. "storage-node-1"
    replica_node_ids VARCHAR(255) NOT NULL, -- Comma-separated replica nodes (e.g. "storage-node-2,storage-node-3")
    status VARCHAR(20) DEFAULT 'ACTIVE' NOT NULL, -- ACTIVE, REPLICATING, FAILED
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 6. Shared Links Table
CREATE TABLE IF NOT EXISTS shared_links (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    token VARCHAR(64) UNIQUE NOT NULL,
    file_id UUID REFERENCES files(id) ON DELETE CASCADE,
    folder_id UUID REFERENCES folders(id) ON DELETE CASCADE,
    creator_id UUID REFERENCES users(id) NOT NULL,
    link_type VARCHAR(20) DEFAULT 'PUBLIC' NOT NULL, -- PUBLIC, PRIVATE, PASSWORD_PROTECTED
    password_hash VARCHAR(255),
    expires_at TIMESTAMP,
    download_count INT DEFAULT 0 NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 7. Audit Logs Table
CREATE TABLE IF NOT EXISTS audit_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID REFERENCES users(id) ON DELETE SET NULL,
    action VARCHAR(50) NOT NULL, -- UPLOAD, DOWNLOAD, DELETE, SHARE
    details TEXT,
    ip_address VARCHAR(45),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Indexes for performance
CREATE INDEX IF NOT EXISTS idx_files_owner ON files(owner_id);
CREATE INDEX IF NOT EXISTS idx_files_folder ON files(folder_id);
CREATE INDEX IF NOT EXISTS idx_folders_owner ON folders(owner_id);
CREATE INDEX IF NOT EXISTS idx_file_chunks_version ON file_chunks(file_version_id);
CREATE INDEX IF NOT EXISTS idx_shared_links_token ON shared_links(token);

-- Seed Data (Optional admin credentials)
-- Default admin user: admin / admin123 (bcrypt hash: $2a$10$f3FvR3iR6w9tZ4L4HqD3uejV1lGz8gXz3h5gQ.P6.8427fE19P/12)
-- Note: Replace this with actual system hash in application. For now, seed a standard password.
INSERT INTO users (id, username, email, password_hash, role, storage_quota_bytes, storage_used_bytes)
VALUES (
    'a3e5c94e-28b9-47bb-a2fc-36104fa282d8',
    'admin',
    'admin@linkstore.io',
    '$2a$10$wKqK4Ldsw2lG0Rz8U.JdDuP3mO.nU2N2pXJ27Z6b4V6d2T6vJ7C1i', -- BCRYPT hash of 'admin123'
    'ADMIN',
    107374182400, -- 100GB
    0
) ON CONFLICT (username) DO NOTHING;
