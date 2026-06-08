import axios from 'axios';

interface ChunkTask {
  chunkIndex: number;
  sizeBytes: number;
  chunkHash: string;
  primaryNodeUrl: string;
  replicaNodeUrls: string[];
}

interface UploadProgressCallback {
  (percentage: number, statusText: string): void;
}

// 1. Calculate SHA-256 hash of entire file in browser using Web Crypto API
export async function calculateFileHash(file: File): Promise<String> {
  const arrayBuffer = await file.arrayBuffer();
  const hashBuffer = await crypto.subtle.digest('SHA-256', arrayBuffer);
  const hashArray = Array.from(new Uint8Array(hashBuffer));
  return hashArray.map(b => b.toString(16).padStart(2, '0')).join('');
}

// 2. Perform parallel chunk uploading
export async function uploadFileParallel(
  file: File,
  token: string,
  onProgress: UploadProgressCallback
): Promise<{ fileId: string; deduplicated: boolean }> {
  
  onProgress(2, "Calculating SHA-256 checksum...");
  const fileHash = await calculateFileHash(file);

  onProgress(10, "Initializing upload session...");
  // Initialize with Metadata service
  const initRes = await axios.post(
    'http://localhost:8080/api/v1/files/upload/init',
    {
      name: file.name,
      sizeBytes: file.size,
      hashSha256: fileHash
    },
    {
      headers: { Authorization: `Bearer ${token}` }
    }
  );

  const { fileId, isDeduplicated, chunks } = initRes.data;

  if (isDeduplicated) {
    onProgress(100, "Upload completed (Deduplication Hit!)");
    return { fileId, deduplicated: true };
  }

  const totalChunks = chunks.length;
  let completedChunks = 0;

  onProgress(15, `Slicing file into ${totalChunks} chunks. Uploading...`);

  // Max concurrency pool size = 3 chunks at a time
  const limitConcurrency = 3;
  const chunkTasks: ChunkTask[] = chunks;
  
  const uploadChunk = async (task: ChunkTask) => {
    const start = task.chunkIndex * 2 * 1024 * 1024; // 2MB chunk boundary
    const end = Math.min(file.size, start + task.sizeBytes);
    const chunkBlob = file.slice(start, end);

    // Create form data
    const formData = new FormData();
    formData.append("chunkHash", task.chunkHash);
    formData.append("file", chunkBlob);

    // Upload directly to primary storage node (mapped through gateway)
    const nodeRoute = task.primaryNodeUrl.replace("http://localhost:", "/api/v1/storage/node");
    
    await axios.post(
      `${nodeRoute}/api/v1/storage/chunks`, 
      formData, 
      {
        headers: { 
          'Content-Type': 'multipart/form-data',
          'Authorization': `Bearer ${token}`
        }
      }
    );

    completedChunks++;
    const progress = Math.min(95, 15 + Math.round((completedChunks / totalChunks) * 80));
    onProgress(progress, `Uploading chunks (${completedChunks}/${totalChunks})...`);
  };

  // Run with bounded concurrency pool
  const pool: Promise<void>[] = [];
  for (const task of chunkTasks) {
    const p = uploadChunk(task).then(() => {
      pool.splice(pool.indexOf(p), 1);
    });
    pool.push(p);
    if (pool.length >= limitConcurrency) {
      await Promise.race(pool);
    }
  }
  await Promise.all(pool);

  onProgress(98, "Re-assembling files on cluster nodes...");
  
  // Complete upload
  await axios.post(
    `http://localhost:8080/api/v1/files/upload/complete`,
    { fileId },
    { headers: { Authorization: `Bearer ${token}` } }
  );

  onProgress(100, "File uploaded successfully!");
  return { fileId, deduplicated: false };
}

// 3. Parallel download and reconstruction inside browser
export async function downloadFileParallel(
  fileId: string,
  token: string,
  onProgress: (percent: number) => void
): Promise<Blob> {
  
  // Fetch chunk download mapping
  const mapRes = await axios.get(
    `http://localhost:8080/api/v1/files/${fileId}/download`,
    { headers: { Authorization: `Bearer ${token}` } }
  );

  const { chunks, fileName } = mapRes.data;
  const totalChunks = chunks.length;
  
  // Allocate buffers array
  const chunkBuffers: ArrayBuffer[] = new Array(totalChunks);
  let downloadedCount = 0;

  const downloadChunk = async (chunk: any) => {
    const nodeRoute = chunk.primaryNodeUrl.replace("http://localhost:", "/api/v1/storage/node");
    const res = await axios.get(
      `${nodeRoute}/api/v1/storage/chunks/${chunk.chunkHash}`,
      {
        responseType: 'arraybuffer',
        headers: { Authorization: `Bearer ${token}` }
      }
    );
    chunkBuffers[chunk.chunkIndex] = res.data;
    downloadedCount++;
    onProgress(Math.round((downloadedCount / totalChunks) * 100));
  };

  // Concurrently download chunks
  const downloadPromises = chunks.map((c: any) => downloadChunk(c));
  await Promise.all(downloadPromises);

  // Merge buffers and trigger client download
  return new Blob(chunkBuffers);
}
