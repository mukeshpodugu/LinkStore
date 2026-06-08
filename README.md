# LinkStore: Production-Grade Distributed File Storage System

LinkStore is a highly available, fault-tolerant, and horizontally scalable distributed file storage system modeled after Amazon S3 and Google Drive. It is built using **Java 21, Spring Boot, PostgreSQL, Redis, RabbitMQ, Docker, Next.js 14, and Tailwind CSS**.

The system demonstrates advanced distributed systems engineering principles, including consistent hashing sharding, multi-node active replication, asynchronous self-healing queues, data deduplication, and concurrent browser-based chunk uploads.

---

## 🚀 Key Features

### 1. Distributed Sharding (Consistent Hashing)
- File chunks are distributed across 4 distinct storage nodes using a custom Java implementation of **Consistent Hashing with Virtual Nodes** (100 vnodes per physical node).
- Minimizes data re-sharding during storage cluster expansion or contraction.

### 2. Configurable Replication & Self-Healing
- Implements a **Write Replication Factor (RF = 2)**.
- A background scheduler polls storage node heartbeats. Upon node failure, the system automatically promotes replicas, updates routing, and publishes repair tasks to **RabbitMQ** to copy missing chunks to healthy nodes.

### 3. Parallel Upload & Download
- **Parallel Uploads**: The client slices files locally into 2MB chunks, computes a SHA-256 hash, fetches the target node topology, and uploads chunks concurrently using a bounded promise pool.
- **Parallel Downloads**: The client concurrently fetches chunk streams directly from sharded storage nodes and merges them in-browser into a single downloadable Blob.

### 4. Storage Optimizations
- **Data Deduplication**: Checks files' SHA-256 hash on upload initialization. If the payload hash already exists in the cluster, LinkStore links the user to the existing version without writing duplicate data.
- **On-the-fly GZIP Compression**: Storage nodes compress chunk arrays before writing to disk, reducing storage footprints by up to 60% for text and code payloads.

### 5. Secure Link Sharing
- Generates 64-character tokenized sharing links supporting **Public**, **Private**, or **Password-Protected** visibilities.
- Allows expiration schedules and offloads download statistic tracking to asynchronous background queues.

---

## 🛠️ Technology Stack

- **Backend Framework**: Java 21, Spring Boot 3.3.x, Spring Data JPA, Spring Cloud Gateway
- **Databases**: PostgreSQL (Relational Metadata), Redis (Cache, Sessions, token-bucket Rate Limiter)
- **Messaging**: RabbitMQ (Asynchronous replication repair and download logs)
- **Frontend**: Next.js 14 (App Router), TypeScript, Tailwind CSS, Lucide Icons
- **Containerization**: Docker, Docker Compose

---

## 📐 Architecture Diagram

```
                        +----------------------------+
                        |  Next.js 14 React Client   |
                        +----------------------------+
                                       | (HTTP/JSON & Binary Streams)
                                       v
                        +----------------------------+
                        |     API Cloud Gateway      | (Port 8080)
                        +----------------------------+
                                /            \
                     (Routes)  /              \ (Direct chunk stream transfer)
                              v                v
                 +-------------------+   +---------------------------------------+
                 | Metadata Service  |   |  Storage Nodes 1 - 4                  |
                 | (Port 8081)       |   |  (Ports 8082-8085)                    |
                 +-------------------+   +---------------------------------------+
                    /        |      \        | (Store chunk files on disk)
                   v         v       v       v
            +----------+ +-------+ +------------+
            | Postgres | | Redis | |  RabbitMQ  |
            | (DB)     | |(Cache)| |  (Broker)  |
            +----------+ +-------+ +------------+
```

---

## 📁 Folder Directory Structure

```
linkstore/
├── docker-compose.yml              # Local docker cluster configurations
├── db/
│   └── schema.sql                  # PostgreSQL database initialization scripts
├── api-gateway/                    # Spring Cloud Gateway routing & rate limiter
├── metadata-service/               # Consistent hashing, folder hierarchy & links orchestrator
├── storage-node/                   # GZIP compression, hashing, and chunk read/write engine
└── frontend/                       # Next.js 14 Tailwind client dashboard
```

---

## 💻 Local Setup & Installation

### Prerequisites
- JDK 21
- Maven 3.8+
- Node.js 18+
- Docker & Docker Compose

### 1. Compile Backend Jars
From the root directory, compile each microservice using Maven:
```bash
cd api-gateway && mvn clean package -DskipTests && cd ..
cd metadata-service && mvn clean package -DskipTests && cd ..
cd storage-node && mvn clean package -DskipTests && cd ..
```

### 2. Run Infrastructure & Services via Docker
Start the entire local cluster (PostgreSQL, Redis, RabbitMQ, API Gateway, Metadata Service, and 4 Storage Nodes):
```bash
docker compose up -d --build
```
Verify all services are running:
```bash
docker compose ps
```

### 3. Run the Next.js Frontend
Navigate to the frontend directory, install dependencies, and start the development server:
```bash
cd frontend
npm install
npm run dev
```
Open [http://localhost:3000](http://localhost:3000) to view the cloud storage client dashboard. 

**Default Login Credentials**:
- **Username**: `admin`
- **Password**: `admin123`

---

## 🛡️ API Endpoints Summary

- **Auth**: `POST /api/v1/auth/register`, `POST /api/v1/auth/login`
- **File Registry**: `POST /api/v1/files/upload/init`, `GET /api/v1/files/{id}/download`, `DELETE /api/v1/files/{id}`
- **Directories**: `POST /api/v1/folders`, `GET /api/v1/folders/{id}`
- **Sharing**: `POST /api/v1/shares`, `GET /api/v1/shares/public/{token}`, `GET /api/v1/shares/public/{token}/download`
- **Dashboards**: `GET /api/v1/dashboards/user`, `GET /api/v1/dashboards/admin` (Requires Admin role)

---

## 👨‍💻 Contact & Developer Details

- **Developer**: **Podugu Mukesh**
- **Phone**: +91 8143999463
- **Email**: [mukeshpodugu123@gmail.com](mailto:mukeshpodugu123@gmail.com)
- **LinkedIn / GitHub**: [github.com/mukeshpodugu](https://github.com/mukeshpodugu)
