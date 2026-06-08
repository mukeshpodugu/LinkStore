'use client';

import React, { useState, useEffect } from 'react';
import { 
  Folder, File, HardDrive, Share2, Shield, Activity, 
  Upload, Download, Trash, RefreshCw, Key, LogOut, 
  Sun, Moon, Users, Check, AlertCircle, Plus
} from 'lucide-react';
import { uploadFileParallel, downloadFileParallel } from '../utils/uploader';
import axios from 'axios';

export default function Home() {
  const [theme, setTheme] = useState<'light' | 'dark'>('dark');
  const [isAuthenticated, setIsAuthenticated] = useState(false);
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [email, setEmail] = useState('');
  const [isRegistering, setIsRegistering] = useState(false);
  const [token, setToken] = useState('');
  const [userId, setUserId] = useState('');
  const [role, setRole] = useState('USER');
  const [errorMsg, setErrorMsg] = useState('');

  // Dashboard Data State
  const [currentTab, setCurrentTab] = useState<'files' | 'shares' | 'stats' | 'admin'>('files');
  const [files, setFiles] = useState<any[]>([]);
  const [folders, setFolders] = useState<any[]>([]);
  const [shares, setShares] = useState<any[]>([]);
  const [userStats, setUserStats] = useState<any>(null);
  const [adminStats, setAdminStats] = useState<any>(null);

  // Upload Tracking State
  const [isUploading, setIsUploading] = useState(false);
  const [uploadPercent, setUploadPercent] = useState(0);
  const [uploadStatus, setUploadStatus] = useState('');

  // New folder creation state
  const [newFolderName, setNewFolderName] = useState('');
  const [showFolderModal, setShowFolderModal] = useState(false);

  // Sharing modal state
  const [selectedFile, setSelectedFile] = useState<any>(null);
  const [shareLinkType, setShareLinkType] = useState('PUBLIC');
  const [sharePassword, setSharePassword] = useState('');
  const [shareExpiry, setShareExpiry] = useState<number>(24);
  const [createdShare, setCreatedShare] = useState<any>(null);

  // Toggle theme
  useEffect(() => {
    const root = window.document.documentElement;
    if (theme === 'dark') {
      root.classList.add('dark');
    } else {
      root.classList.remove('dark');
    }
  }, [theme]);

  // Load files and stats when token changes
  useEffect(() => {
    if (token) {
      loadDashboardData();
    }
  }, [token]);

  const loadDashboardData = async () => {
    try {
      // Load user stats
      const statsRes = await axios.get('http://localhost:8080/api/v1/dashboards/user', {
        headers: { Authorization: `Bearer ${token}` }
      });
      setUserStats(statsRes.data);

      // Load files
      const filesRes = await axios.get('http://localhost:8080/api/v1/files', {
        headers: { Authorization: `Bearer ${token}` }
      });
      setFiles(filesRes.data);

      // Load folders
      const foldersRes = await axios.get('http://localhost:8080/api/v1/folders', {
        headers: { Authorization: `Bearer ${token}` }
      });
      setFolders(foldersRes.data);

      // Load admin stats if admin
      if (role === 'ADMIN') {
        const adminRes = await axios.get('http://localhost:8080/api/v1/dashboards/admin', {
          headers: { Authorization: `Bearer ${token}` }
        });
        setAdminStats(adminRes.data);
      }
    } catch (err) {
      console.error("Error loading dashboard data", err);
    }
  };

  const handleLogin = async (e: React.FormEvent) => {
    e.preventDefault();
    setErrorMsg('');
    try {
      const res = await axios.post('http://localhost:8080/api/v1/auth/login', { username, password });
      setToken(res.data.token);
      setUserId(res.data.userId);
      setRole(res.data.role);
      setIsAuthenticated(true);
    } catch (err: any) {
      setErrorMsg(err.response?.data?.message || 'Login failed. Please verify credentials.');
    }
  };

  const handleRegister = async (e: React.FormEvent) => {
    e.preventDefault();
    setErrorMsg('');
    try {
      const res = await axios.post('http://localhost:8080/api/v1/auth/register', { username, email, password });
      setToken(res.data.token);
      setUserId(res.data.userId);
      setRole(res.data.role);
      setIsAuthenticated(true);
    } catch (err: any) {
      setErrorMsg(err.response?.data?.message || 'Registration failed.');
    }
  };

  const handleLogout = () => {
    setIsAuthenticated(false);
    setToken('');
    setUserId('');
    setRole('USER');
    setUsername('');
    setPassword('');
  };

  // Drag and drop upload handler
  const handleDrop = async (e: React.DragEvent) => {
    e.preventDefault();
    const filesList = e.dataTransfer.files;
    if (filesList.length > 0) {
      const fileToUpload = filesList[0];
      await triggerUpload(fileToUpload);
    }
  };

  const handleFileChange = async (e: React.ChangeEvent<HTMLInputElement>) => {
    if (e.target.files && e.target.files.length > 0) {
      const fileToUpload = e.target.files[0];
      await triggerUpload(fileToUpload);
    }
  };

  const triggerUpload = async (file: File) => {
    setIsUploading(true);
    setUploadPercent(0);
    setUploadStatus('Processing upload...');
    try {
      await uploadFileParallel(file, token, (percent, status) => {
        setUploadPercent(percent);
        setUploadStatus(status);
      });
      loadDashboardData();
    } catch (err: any) {
      console.error(err);
      setUploadStatus('Upload failed: ' + (err.response?.data?.message || err.message));
    } finally {
      setTimeout(() => setIsUploading(false), 2000);
    }
  };

  const triggerDownload = async (fileId: string, name: string) => {
    try {
      const blob = await downloadFileParallel(fileId, token, (percent) => {
        console.log(`Downloading ${name}: ${percent}%`);
      });
      const url = window.URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = url;
      a.download = name;
      document.body.appendChild(a);
      a.click();
      a.remove();
    } catch (err) {
      alert("Download failed. Physical storage node might be offline!");
    }
  };

  const handleDelete = async (fileId: string) => {
    if (confirm("Are you sure you want to delete this file?")) {
      try {
        await axios.delete(`http://localhost:8080/api/v1/files/${fileId}`, {
          headers: { Authorization: `Bearer ${token}` }
        });
        loadDashboardData();
      } catch (err) {
        alert("Deletion failed.");
      }
    }
  };

  const handleCreateFolder = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      await axios.post('http://localhost:8080/api/v1/folders', {
        name: newFolderName,
        parentId: ""
      }, {
        headers: { Authorization: `Bearer ${token}` }
      });
      setNewFolderName('');
      setShowFolderModal(false);
      loadDashboardData();
    } catch (err: any) {
      alert(err.response?.data?.message || "Folder creation failed");
    }
  };

  const handleCreateShareLink = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      const res = await axios.post('http://localhost:8080/api/v1/shares', {
        fileId: selectedFile.id,
        linkType: shareLinkType,
        password: sharePassword,
        expirationHours: shareExpiry
      }, {
        headers: { Authorization: `Bearer ${token}` }
      });
      setCreatedShare(res.data);
      loadDashboardData();
    } catch (err: any) {
      alert(err.response?.data?.message || "Sharing failed");
    }
  };

  const formatBytes = (bytes: number) => {
    if (bytes === 0) return '0 Bytes';
    const k = 1024;
    const sizes = ['Bytes', 'KB', 'MB', 'GB', 'TB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
  };

  if (!isAuthenticated) {
    return (
      <div className="flex items-center justify-center min-h-screen bg-slate-900 text-slate-100 p-6 relative overflow-hidden">
        {/* Abstract blur glow backgrounds */}
        <div className="absolute top-[-20%] left-[-20%] w-[60%] h-[60%] bg-indigo-900 rounded-full filter blur-[150px] opacity-40"></div>
        <div className="absolute bottom-[-20%] right-[-20%] w-[60%] h-[60%] bg-violet-900 rounded-full filter blur-[150px] opacity-40"></div>

        <div className="w-full max-w-md glass-panel glass-panel-glow border-slate-800 p-8 flex flex-col items-center z-10 relative">
          <div className="flex items-center gap-2 mb-6">
            <HardDrive className="h-8 w-8 text-indigo-400 animate-pulse" />
            <h1 className="text-3xl font-extrabold tracking-tight bg-clip-text text-transparent bg-gradient-to-r from-indigo-400 to-violet-400">
              LinkStore
            </h1>
          </div>

          <p className="text-sm text-slate-400 text-center mb-6">
            Distributed storage simulator with consistent hashing sharding, replica replication, and GZIP chunk compression.
          </p>

          <form onSubmit={isRegistering ? handleRegister : handleLogin} className="w-full space-y-4">
            <div>
              <label className="text-xs text-slate-400 uppercase font-semibold">Username</label>
              <input 
                type="text" 
                required
                className="w-full bg-slate-950 border border-slate-800 rounded-lg px-4 py-2 mt-1 focus:outline-none focus:border-indigo-500 text-slate-200"
                value={username}
                onChange={e => setUsername(e.target.value)}
              />
            </div>

            {isRegistering && (
              <div>
                <label className="text-xs text-slate-400 uppercase font-semibold">Email</label>
                <input 
                  type="email" 
                  required
                  className="w-full bg-slate-950 border border-slate-800 rounded-lg px-4 py-2 mt-1 focus:outline-none focus:border-indigo-500 text-slate-200"
                  value={email}
                  onChange={e => setEmail(e.target.value)}
                />
              </div>
            )}

            <div>
              <label className="text-xs text-slate-400 uppercase font-semibold">Password</label>
              <input 
                type="password" 
                required
                className="w-full bg-slate-950 border border-slate-800 rounded-lg px-4 py-2 mt-1 focus:outline-none focus:border-indigo-500 text-slate-200"
                value={password}
                onChange={e => setPassword(e.target.value)}
              />
            </div>

            {errorMsg && (
              <div className="flex items-center gap-2 text-red-400 bg-red-950/50 border border-red-900 rounded-lg p-3 text-xs">
                <AlertCircle className="h-4 w-4 shrink-0" />
                <span>{errorMsg}</span>
              </div>
            )}

            <button 
              type="submit"
              className="w-full py-3 bg-gradient-to-r from-indigo-600 to-violet-600 rounded-lg font-bold text-white shadow-lg hover:shadow-indigo-500/20 hover:from-indigo-500 hover:to-violet-500 transition-all"
            >
              {isRegistering ? 'Register & Initialize' : 'Sign In'}
            </button>
          </form>

          <button 
            onClick={() => setIsRegistering(!isRegistering)}
            className="text-xs text-indigo-400 mt-4 hover:underline"
          >
            {isRegistering ? 'Already have an account? Sign In' : 'Need an account? Register Here'}
          </button>
        </div>
      </div>
    );
  }

  return (
    <div className={`min-h-screen flex flex-col ${theme === 'dark' ? 'bg-slate-950 text-slate-100' : 'bg-slate-50 text-slate-900'}`}>
      {/* Header */}
      <header className="h-16 flex items-center justify-between px-6 border-b border-slate-800/20 glass-panel border-r-0 border-l-0 border-t-0 rounded-none z-20">
        <div className="flex items-center gap-2">
          <HardDrive className="h-6 w-6 text-indigo-500" />
          <span className="font-extrabold text-xl bg-clip-text text-transparent bg-gradient-to-r from-indigo-500 to-violet-500">
            LinkStore
          </span>
        </div>

        <div className="flex items-center gap-4">
          <button 
            onClick={() => setTheme(theme === 'dark' ? 'light' : 'dark')}
            className="p-2 rounded-lg border border-slate-800/20 hover:bg-slate-500/10 transition-colors"
          >
            {theme === 'dark' ? <Sun className="h-5 w-5 text-amber-400" /> : <Moon className="h-5 w-5 text-slate-600" />}
          </button>

          <div className="flex items-center gap-2">
            <span className="text-xs px-2.5 py-1 bg-indigo-500/10 border border-indigo-500/20 rounded-full font-semibold text-indigo-400">
              {role}
            </span>
            <span className="text-sm font-medium">{username}</span>
          </div>

          <button 
            onClick={handleLogout}
            className="flex items-center gap-1.5 text-xs text-red-400 hover:text-red-300 font-semibold transition-colors"
          >
            <LogOut className="h-4 w-4" />
            Logout
          </button>
        </div>
      </header>

      {/* Main Panel */}
      <div className="flex-1 flex overflow-hidden">
        {/* Sidebar */}
        <aside className="w-64 border-r border-slate-800/20 p-6 flex flex-col justify-between">
          <div className="space-y-6">
            <div>
              <p className="text-xs font-semibold text-slate-400 uppercase tracking-wider mb-3">Storage</p>
              <nav className="space-y-1">
                <button 
                  onClick={() => setCurrentTab('files')}
                  className={`w-full flex items-center gap-3 px-4 py-2.5 rounded-lg text-sm font-medium transition-all ${currentTab === 'files' ? 'bg-indigo-500/15 text-indigo-400' : 'text-slate-400 hover:bg-slate-500/5 hover:text-slate-200'}`}
                >
                  <Folder className="h-5 w-5" />
                  My Files
                </button>
                <button 
                  onClick={() => setCurrentTab('shares')}
                  className={`w-full flex items-center gap-3 px-4 py-2.5 rounded-lg text-sm font-medium transition-all ${currentTab === 'shares' ? 'bg-indigo-500/15 text-indigo-400' : 'text-slate-400 hover:bg-slate-500/5 hover:text-slate-200'}`}
                >
                  <Share2 className="h-5 w-5" />
                  Shared Links
                </button>
                <button 
                  onClick={() => setCurrentTab('stats')}
                  className={`w-full flex items-center gap-3 px-4 py-2.5 rounded-lg text-sm font-medium transition-all ${currentTab === 'stats' ? 'bg-indigo-500/15 text-indigo-400' : 'text-slate-400 hover:bg-slate-500/5 hover:text-slate-200'}`}
                >
                  <Activity className="h-5 w-5" />
                  System Metrics
                </button>
              </nav>
            </div>

            {role === 'ADMIN' && (
              <div>
                <p className="text-xs font-semibold text-slate-400 uppercase tracking-wider mb-3">Admin Console</p>
                <nav className="space-y-1">
                  <button 
                    onClick={() => setCurrentTab('admin')}
                    className={`w-full flex items-center gap-3 px-4 py-2.5 rounded-lg text-sm font-medium transition-all ${currentTab === 'admin' ? 'bg-indigo-500/15 text-indigo-400' : 'text-slate-400 hover:bg-slate-500/5 hover:text-slate-200'}`}
                  >
                    <Shield className="h-5 w-5" />
                    Storage Cluster
                  </button>
                </nav>
              </div>
            )}
          </div>

          {/* Quota overview */}
          {userStats && (
            <div className="glass-panel p-4 border-slate-800/10">
              <div className="flex items-center gap-2 mb-2">
                <HardDrive className="h-4 w-4 text-indigo-400" />
                <span className="text-xs font-semibold text-slate-400">Disk Quota</span>
              </div>
              <div className="h-2 w-full bg-slate-800 rounded-full overflow-hidden">
                <div 
                  className="h-full bg-indigo-500 rounded-full" 
                  style={{ width: `${Math.round((userStats.storageUsedBytes / userStats.storageQuotaBytes) * 100)}%` }}
                ></div>
              </div>
              <div className="flex justify-between text-[10px] text-slate-400 mt-2">
                <span>{formatBytes(userStats.storageUsedBytes)}</span>
                <span>{formatBytes(userStats.storageQuotaBytes)}</span>
              </div>
            </div>
          )}
        </aside>

        {/* Content Panel */}
        <main className="flex-1 p-8 overflow-y-auto">
          {currentTab === 'files' && (
            <div className="space-y-8">
              <div className="flex items-center justify-between">
                <div>
                  <h2 className="text-2xl font-bold tracking-tight">My Storage Vault</h2>
                  <p className="text-sm text-slate-400">Drag and drop file to slice, compress, and shard on target nodes.</p>
                </div>
                <div className="flex gap-3">
                  <button 
                    onClick={() => setShowFolderModal(true)}
                    className="flex items-center gap-2 px-4 py-2 border border-slate-800/20 rounded-lg text-sm hover:bg-slate-500/5 transition-colors"
                  >
                    <Plus className="h-4 w-4" />
                    New Folder
                  </button>
                  <label className="flex items-center gap-2 px-4 py-2 bg-indigo-600 hover:bg-indigo-500 rounded-lg text-sm font-semibold cursor-pointer transition-colors text-white">
                    <Upload className="h-4 w-4" />
                    Upload File
                    <input type="file" className="hidden" onChange={handleFileChange} />
                  </label>
                </div>
              </div>

              {/* Progress Panel */}
              {isUploading && (
                <div className="glass-panel p-6 border-indigo-500/20 bg-indigo-500/5 flex flex-col gap-3 animate-pulse">
                  <div className="flex justify-between items-center text-xs">
                    <span className="font-semibold text-indigo-400">{uploadStatus}</span>
                    <span className="font-bold">{uploadPercent}%</span>
                  </div>
                  <div className="h-2 w-full bg-slate-800 rounded-full overflow-hidden">
                    <div className="h-full bg-indigo-500 rounded-full transition-all duration-300" style={{ width: `${uploadPercent}%` }}></div>
                  </div>
                </div>
              )}

              {/* Drop Zone */}
              <div 
                onDragOver={e => e.preventDefault()}
                onDrop={handleDrop}
                className="border-2 border-dashed border-slate-800/30 rounded-xl p-10 flex flex-col items-center justify-center text-slate-400 hover:border-indigo-500/30 hover:bg-indigo-500/[0.01] transition-all"
              >
                <Upload className="h-10 w-10 text-indigo-400/50 mb-3 animate-bounce" />
                <p className="text-sm font-medium">Drag & drop files here, or click to upload</p>
                <p className="text-xs text-slate-500 mt-1">Chunked up to 2MB, replication factor of 2, auto GZIP compression</p>
              </div>

              {/* Files Grid */}
              <div className="space-y-4">
                <h3 className="text-sm font-semibold text-slate-400 uppercase tracking-wider">All Items</h3>
                <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
                  {folders.map(folder => (
                    <div key={folder.id} className="glass-panel p-4 flex items-center gap-3 hover-scale cursor-pointer">
                      <div className="p-3 bg-amber-500/10 rounded-lg text-amber-500">
                        <Folder className="h-6 w-6" />
                      </div>
                      <div>
                        <p className="font-semibold text-sm truncate">{folder.name}</p>
                        <p className="text-xs text-slate-400">Directory</p>
                      </div>
                    </div>
                  ))}

                  {files.map(file => (
                    <div key={file.id} className="glass-panel p-4 flex flex-col justify-between hover-scale group relative min-h-[140px]">
                      <div className="flex items-start justify-between gap-2">
                        <div className="flex items-center gap-3">
                          <div className="p-3 bg-indigo-500/10 rounded-lg text-indigo-500">
                            <File className="h-6 w-6" />
                          </div>
                          <div className="max-w-[140px]">
                            <p className="font-semibold text-sm truncate" title={file.name}>{file.name}</p>
                            <p className="text-xs text-slate-400">{formatBytes(file.sizeBytes)}</p>
                          </div>
                        </div>
                      </div>

                      <div className="flex items-center justify-between border-t border-slate-800/10 pt-3 mt-4">
                        <div className="flex gap-2">
                          <button 
                            onClick={() => triggerDownload(file.id, file.name)}
                            className="p-2 rounded hover:bg-slate-500/10 text-slate-400 hover:text-slate-200 transition-colors"
                            title="Download File"
                          >
                            <Download className="h-4 w-4" />
                          </button>
                          <button 
                            onClick={() => { setSelectedFile(file); setCreatedShare(null); }}
                            className="p-2 rounded hover:bg-slate-500/10 text-slate-400 hover:text-slate-200 transition-colors"
                            title="Share Link"
                          >
                            <Share2 className="h-4 w-4" />
                          </button>
                        </div>
                        <button 
                          onClick={() => handleDelete(file.id)}
                          className="p-2 rounded hover:bg-red-500/10 text-slate-400 hover:text-red-400 transition-colors"
                          title="Delete File"
                        >
                          <Trash className="h-4 w-4" />
                        </button>
                      </div>
                    </div>
                  ))}
                </div>
              </div>
            </div>
          )}

          {currentTab === 'shares' && (
            <div className="space-y-6">
              <div>
                <h2 className="text-2xl font-bold tracking-tight">Shared Link Trackers</h2>
                <p className="text-sm text-slate-400">Monitor access statistics, expiration schedules, and link permissions.</p>
              </div>

              <div className="glass-panel overflow-hidden border-slate-800/10">
                <table className="w-full text-left border-collapse">
                  <thead>
                    <tr className="border-b border-slate-800/10 text-slate-400 text-xs uppercase font-semibold">
                      <th className="p-4">Item Name</th>
                      <th className="p-4">Visibility</th>
                      <th className="p-4">Expires At</th>
                      <th className="p-4">Downloads</th>
                      <th className="p-4">Actions</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-800/10 text-sm">
                    {files.map(file => (
                      <tr key={file.id} className="hover:bg-slate-500/[0.01]">
                        <td className="p-4 flex items-center gap-2">
                          <File className="h-4 w-4 text-indigo-400" />
                          <span>{file.name}</span>
                        </td>
                        <td className="p-4">
                          <span className="px-2 py-0.5 bg-emerald-500/10 text-emerald-400 border border-emerald-500/20 text-xs rounded-full font-semibold">
                            PUBLIC
                          </span>
                        </td>
                        <td className="p-4 text-slate-400">24h expiration</td>
                        <td className="p-4 font-bold text-indigo-400">12 downloads</td>
                        <td className="p-4">
                          <button className="text-xs font-semibold text-indigo-400 hover:underline">Copy Link</button>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          )}

          {currentTab === 'stats' && userStats && (
            <div className="space-y-8">
              <div>
                <h2 className="text-2xl font-bold tracking-tight">System Metrics</h2>
                <p className="text-sm text-slate-400">Real-time statistics of user storage space and transactions.</p>
              </div>

              <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
                <div className="glass-panel p-6 flex items-center justify-between">
                  <div>
                    <p className="text-xs text-slate-400 uppercase font-semibold mb-1">Total Files</p>
                    <p className="text-3xl font-extrabold">{userStats.filesCount}</p>
                  </div>
                  <div className="p-4 bg-indigo-500/10 rounded-xl text-indigo-400">
                    <File className="h-8 w-8" />
                  </div>
                </div>

                <div className="glass-panel p-6 flex items-center justify-between">
                  <div>
                    <p className="text-xs text-slate-400 uppercase font-semibold mb-1">Shared Files</p>
                    <p className="text-3xl font-extrabold">{userStats.sharedLinksCount}</p>
                  </div>
                  <div className="p-4 bg-indigo-500/10 rounded-xl text-indigo-400">
                    <Share2 className="h-8 w-8" />
                  </div>
                </div>

                <div className="glass-panel p-6 flex items-center justify-between">
                  <div>
                    <p className="text-xs text-slate-400 uppercase font-semibold mb-1">Storage Usage</p>
                    <p className="text-3xl font-extrabold">{Math.round((userStats.storageUsedBytes / userStats.storageQuotaBytes) * 100)}%</p>
                  </div>
                  <div className="p-4 bg-indigo-500/10 rounded-xl text-indigo-400">
                    <HardDrive className="h-8 w-8" />
                  </div>
                </div>
              </div>

              {/* Activity log */}
              <div className="space-y-4">
                <h3 className="text-lg font-bold">Recent Activities</h3>
                <div className="glass-panel p-6 space-y-4 divide-y divide-slate-800/10">
                  {userStats.recentActivities.map((act: any, idx: number) => (
                    <div key={idx} className={`flex justify-between items-center text-sm ${idx > 0 ? 'pt-4' : ''}`}>
                      <div className="flex gap-3 items-center">
                        <Activity className="h-4 w-4 text-indigo-400" />
                        <div>
                          <p className="font-semibold">{act.action}</p>
                          <p className="text-xs text-slate-400">{act.details}</p>
                        </div>
                      </div>
                      <span className="text-xs text-slate-400">{act.timestamp}</span>
                    </div>
                  ))}
                  {userStats.recentActivities.length === 0 && (
                    <p className="text-sm text-slate-400">No activity recorded yet.</p>
                  )}
                </div>
              </div>
            </div>
          )}

          {currentTab === 'admin' && adminStats && (
            <div className="space-y-8">
              <div>
                <h2 className="text-2xl font-bold tracking-tight">Storage Cluster Health Console</h2>
                <p className="text-sm text-slate-400">Monitor nodes statuses, chunk replication schedules, and cache hit metrics.</p>
              </div>

              {/* Node status list */}
              <div className="space-y-4">
                <h3 className="text-sm font-semibold text-slate-400 uppercase tracking-wider">Storage Node Topology</h3>
                <div className="grid grid-cols-1 md:grid-cols-4 gap-6">
                  {adminStats.nodes.map((node: any) => (
                    <div key={node.nodeId} className="glass-panel p-6 space-y-4 relative overflow-hidden">
                      <div className="flex items-center justify-between">
                        <span className="font-bold text-sm">{node.nodeId}</span>
                        <span className={`h-2.5 w-2.5 rounded-full ${node.status === 'UP' ? 'bg-emerald-500 animate-pulse' : 'bg-red-500'}`}></span>
                      </div>
                      <div>
                        <p className="text-xs text-slate-400">Status</p>
                        <p className="font-semibold text-sm">{node.status === 'UP' ? 'ACTIVE' : 'FAILED / DOWN'}</p>
                      </div>
                      <div className="flex justify-between text-xs text-slate-400">
                        <span>Chunks: {node.chunkCount}</span>
                        <span>{formatBytes(node.storageUsedBytes)}</span>
                      </div>
                      <p className="text-[10px] text-slate-500 font-mono">{node.url}</p>
                    </div>
                  ))}
                </div>
              </div>

              {/* Cache metrics */}
              <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
                <div className="glass-panel p-6 space-y-4">
                  <h3 className="font-bold text-md">Redis Caching Efficiency</h3>
                  <div className="flex justify-between items-center border-b border-slate-800/10 pb-3">
                    <span className="text-sm text-slate-400">Cache Hits</span>
                    <span className="font-bold">{adminStats.cacheStats.hits}</span>
                  </div>
                  <div className="flex justify-between items-center border-b border-slate-800/10 pb-3">
                    <span className="text-sm text-slate-400">Cache Misses</span>
                    <span className="font-bold">{adminStats.cacheStats.misses}</span>
                  </div>
                  <div className="flex justify-between items-center pb-3">
                    <span className="text-sm text-slate-400">Cache Hit Ratio</span>
                    <span className="font-bold text-indigo-400">{Math.round(adminStats.cacheStats.hitRatio * 100)}%</span>
                  </div>
                </div>

                <div className="glass-panel p-6 space-y-4">
                  <h3 className="font-bold text-md">System Statistics</h3>
                  <div className="flex justify-between items-center border-b border-slate-800/10 pb-3">
                    <span className="text-sm text-slate-400">Throughput (Mbps)</span>
                    <span className="font-bold text-indigo-400">{adminStats.systemMetrics.systemThroughputMbps} Mbps</span>
                  </div>
                  <div className="flex justify-between items-center border-b border-slate-800/10 pb-3">
                    <span className="text-sm text-slate-400">Active Cluster Users</span>
                    <span className="font-bold">{adminStats.activeUsersCount}</span>
                  </div>
                  <div className="flex justify-between items-center pb-3">
                    <span className="text-sm text-slate-400">Write Errors (24h)</span>
                    <span className={`font-bold ${adminStats.systemMetrics.errorRatePercentage > 0 ? 'text-red-400' : 'text-emerald-400'}`}>
                      {adminStats.systemMetrics.errorRatePercentage}%
                    </span>
                  </div>
                </div>
              </div>
            </div>
          )}
        </main>
      </div>

      {/* New Folder Modal */}
      {showFolderModal && (
        <div className="fixed inset-0 bg-black/60 backdrop-blur-xs flex items-center justify-center p-4 z-50">
          <div className="glass-panel p-6 max-w-sm w-full bg-slate-900 border-slate-800 space-y-4">
            <h3 className="text-lg font-bold">Create Directory</h3>
            <form onSubmit={handleCreateFolder} className="space-y-4">
              <input 
                type="text" 
                placeholder="Folder name" 
                required
                className="w-full bg-slate-950 border border-slate-800 rounded-lg px-4 py-2 focus:outline-none focus:border-indigo-500 text-slate-200"
                value={newFolderName}
                onChange={e => setNewFolderName(e.target.value)}
              />
              <div className="flex justify-end gap-3 text-sm font-semibold">
                <button type="button" onClick={() => setShowFolderModal(false)} className="px-4 py-2 hover:bg-slate-500/10 rounded-lg">Cancel</button>
                <button type="submit" className="px-4 py-2 bg-indigo-600 hover:bg-indigo-500 rounded-lg text-white">Create</button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Share Modal */}
      {selectedFile && (
        <div className="fixed inset-0 bg-black/60 backdrop-blur-xs flex items-center justify-center p-4 z-50">
          <div className="glass-panel p-6 max-w-md w-full bg-slate-900 border-slate-800 space-y-6">
            <h3 className="text-lg font-bold truncate">Share: {selectedFile.name}</h3>

            {!createdShare ? (
              <form onSubmit={handleCreateShareLink} className="space-y-4">
                <div>
                  <label className="text-xs text-slate-400 uppercase font-semibold">Link Access Policy</label>
                  <select 
                    className="w-full bg-slate-950 border border-slate-800 rounded-lg px-4 py-2 mt-1 focus:outline-none focus:border-indigo-500 text-slate-200"
                    value={shareLinkType}
                    onChange={e => setShareLinkType(e.target.value)}
                  >
                    <option value="PUBLIC">Public Shareable Link</option>
                    <option value="PASSWORD_PROTECTED">Password Protected Link</option>
                    <option value="PRIVATE">Private Link (Owner Access Only)</option>
                  </select>
                </div>

                {shareLinkType === 'PASSWORD_PROTECTED' && (
                  <div>
                    <label className="text-xs text-slate-400 uppercase font-semibold">Security Password</label>
                    <input 
                      type="password" 
                      placeholder="Min 6 characters"
                      required
                      className="w-full bg-slate-950 border border-slate-800 rounded-lg px-4 py-2 mt-1 focus:outline-none focus:border-indigo-500 text-slate-200"
                      value={sharePassword}
                      onChange={e => setSharePassword(e.target.value)}
                    />
                  </div>
                )}

                <div>
                  <label className="text-xs text-slate-400 uppercase font-semibold">Expiration Schedule</label>
                  <select 
                    className="w-full bg-slate-950 border border-slate-800 rounded-lg px-4 py-2 mt-1 focus:outline-none focus:border-indigo-500 text-slate-200"
                    value={shareExpiry}
                    onChange={e => setShareExpiry(Number(e.target.value))}
                  >
                    <option value={1}>Expires in 1 Hour</option>
                    <option value={24}>Expires in 24 Hours</option>
                    <option value={168}>Expires in 7 Days</option>
                    <option value={0}>Never Expire</option>
                  </select>
                </div>

                <div className="flex justify-end gap-3 text-sm font-semibold pt-4">
                  <button type="button" onClick={() => setSelectedFile(null)} className="px-4 py-2 hover:bg-slate-500/10 rounded-lg">Close</button>
                  <button type="submit" className="px-4 py-2 bg-indigo-600 hover:bg-indigo-500 rounded-lg text-white">Generate Link</button>
                </div>
              </form>
            ) : (
              <div className="space-y-4">
                <div className="flex items-center gap-2 text-emerald-400 bg-emerald-950/30 border border-emerald-900 p-3 rounded-lg text-xs">
                  <Check className="h-4 w-4 shrink-0" />
                  <span>Your secure download token has been generated successfully!</span>
                </div>
                <div>
                  <label className="text-xs text-slate-400 uppercase font-semibold">Public Link URL</label>
                  <input 
                    type="text" 
                    readOnly
                    className="w-full bg-slate-950 border border-slate-800 rounded-lg px-4 py-2 mt-1 focus:outline-none text-slate-300 font-mono text-xs"
                    value={createdShare.downloadUrl}
                    onClick={e => (e.target as HTMLInputElement).select()}
                  />
                </div>
                <div className="flex justify-end pt-4">
                  <button 
                    type="button" 
                    onClick={() => setSelectedFile(null)} 
                    className="px-4 py-2 bg-indigo-600 hover:bg-indigo-500 rounded-lg text-sm font-semibold text-white"
                  >
                    Done
                  </button>
                </div>
              </div>
            )}
          </div>
        </div>
      )}
    </div>
  );
}
