import { apiClient } from './client';
import type {
  AdminDashboardSummary,
  AiAuditLogItem,
  AuthResponse,
  BatchUploadResponse,
  ChatMessageItem,
  ConversationItem,
  DocumentDetail,
  DocumentItem,
  DocumentStatus,
  DocumentStatusInfo,
  PagedResponse,
  RagAnswerResponse,
  UserDashboardSummary,
  UserProfile,
} from '../types/api';

export const authApi = {
  register: async (payload: { fullName: string; email: string; password: string }) => {
    const { data } = await apiClient.post<AuthResponse>('/auth/register', payload);
    return data;
  },
  login: async (payload: { email: string; password: string }) => {
    const { data } = await apiClient.post<AuthResponse>('/auth/login', payload);
    return data;
  },
  logout: async () => {
    await apiClient.post('/auth/logout');
  },
  me: async () => {
    const { data } = await apiClient.get<UserProfile>('/auth/me');
    return data;
  },
};

export const dashboardApi = {
  summary: async () => {
    const { data } = await apiClient.get<UserDashboardSummary>('/dashboard/summary');
    return data;
  },
};

export const documentsApi = {
  list: async (params: { page?: number; size?: number; status?: DocumentStatus | ''; search?: string }) => {
    const { data } = await apiClient.get<PagedResponse<DocumentItem>>('/documents', {
      params: {
        page: params.page ?? 0,
        size: params.size ?? 10,
        ...(params.status ? { status: params.status } : {}),
        ...(params.search?.trim() ? { search: params.search.trim() } : {}),
      },
    });
    return data;
  },
  uploadSingle: async (file: File) => {
    const formData = new FormData();
    formData.append('file', file);
    const { data } = await apiClient.post<DocumentItem>('/documents/upload', formData);
    return data;
  },
  uploadMultiple: async (files: File[]) => {
    const formData = new FormData();
    files.forEach((f) => formData.append('files', f));
    const { data } = await apiClient.post<BatchUploadResponse>('/documents/upload-multiple', formData);
    return data;
  },
  details: async (id: string) => {
    const { data } = await apiClient.get<DocumentDetail>(`/documents/${id}`);
    return data;
  },
  status: async (id: string) => {
    const { data } = await apiClient.get<DocumentStatusInfo>(`/documents/${id}/status`);
    return data;
  },
  reprocess: async (id: string) => {
    const { data } = await apiClient.post<DocumentStatusInfo>(`/documents/${id}/reprocess`);
    return data;
  },
  remove: async (id: string) => {
    await apiClient.delete(`/documents/${id}`);
  },
};

export const conversationsApi = {
  list: async (page = 0, size = 30) => {
    const { data } = await apiClient.get<PagedResponse<ConversationItem>>('/conversations', {
      params: { page, size },
    });
    return data;
  },
  create: async (payload: { title?: string; documentId?: string | null }) => {
    const { data } = await apiClient.post<ConversationItem>('/conversations', payload);
    return data;
  },
  get: async (id: string) => {
    const { data } = await apiClient.get<ConversationItem>(`/conversations/${id}`);
    return data;
  },
  rename: async (id: string, title: string) => {
    const { data } = await apiClient.patch<ConversationItem>(`/conversations/${id}`, { title });
    return data;
  },
  remove: async (id: string) => {
    await apiClient.delete(`/conversations/${id}`);
  },
  messages: async (id: string, page = 0, size = 100) => {
    const { data } = await apiClient.get<PagedResponse<ChatMessageItem>>(`/conversations/${id}/messages`, {
      params: { page, size },
    });
    return data;
  },
};

export const ragApi = {
  ask: async (payload: {
    conversationId?: string | null;
    documentId?: string | null;
    question: string;
    topK?: number;
    similarityThreshold?: number;
    temperature?: number;
  }) => {
    const { data } = await apiClient.post<RagAnswerResponse>('/rag/ask', payload);
    return data;
  },
  chatInConversation: async (
    conversationId: string,
    payload: {
      question: string;
      documentId?: string | null;
      topK?: number;
      similarityThreshold?: number;
      temperature?: number;
    }
  ) => {
    const { data } = await apiClient.post<RagAnswerResponse>(`/conversations/${conversationId}/chat`, payload);
    return data;
  },
};

export const adminApi = {
  dashboard: async () => {
    const { data } = await apiClient.get<AdminDashboardSummary>('/admin/dashboard');
    return data;
  },
  users: async (page = 0, size = 10) => {
    const { data } = await apiClient.get<PagedResponse<UserProfile>>('/admin/users', {
      params: { page, size },
    });
    return data;
  },
  aiUsage: async (page = 0, size = 15) => {
    const { data } = await apiClient.get<PagedResponse<AiAuditLogItem>>('/admin/ai-usage', {
      params: { page, size },
    });
    return data;
  },
};
