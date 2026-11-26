export type DocumentStatus = 'UPLOADED' | 'PROCESSING' | 'COMPLETED' | 'FAILED' | 'DELETING';
export type MessageRole = 'USER' | 'ASSISTANT' | 'SYSTEM';

export interface UserProfile {
  id: string;
  email: string;
  fullName: string;
  roles: string[];
  enabled: boolean;
  createdAt: string;
}

export interface AuthResponse {
  accessToken: string;
  tokenType: string;
  expiresInMs: number;
  user: UserProfile;
}

export interface PagedResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
}

export interface DocumentItem {
  id: string;
  originalFilename: string;
  mimeType: string;
  fileSize: number;
  status: DocumentStatus;
  processingError: string | null;
  totalPages: number | null;
  totalChunks: number;
  uploadedAt: string;
  lastProcessedAt: string | null;
}

export interface DocumentProcessingLog {
  id: string;
  stage: string;
  status: string;
  message: string | null;
  durationMs: number | null;
  createdAt: string;
}

export interface DocumentDetail extends DocumentItem {
  ownerId: string;
  fileChecksum: string;
  extractionSummary: string | null;
  updatedAt: string;
  processingLogs: DocumentProcessingLog[];
}

export interface DocumentStatusInfo {
  id: string;
  status: DocumentStatus;
  processingError: string | null;
  totalPages: number | null;
  totalChunks: number;
  lastProcessedAt: string | null;
}

export interface BatchUploadResponse {
  uploaded: DocumentItem[];
  failures: {
    filename: string;
    errorCode: string;
    message: string;
  }[];
}

export interface SourceCitation {
  documentId: string | null;
  documentName: string;
  pageNumber: number | null;
  sectionTitle: string | null;
  chunkId: string;
  relevanceScore: number | null;
  excerpt: string;
}

export interface TokenUsage {
  promptTokens: number | null;
  completionTokens: number | null;
  totalTokens: number | null;
}

export interface ConversationItem {
  id: string;
  documentId: string | null;
  documentName: string | null;
  title: string;
  createdAt: string;
  updatedAt: string;
}

export interface ChatMessageItem {
  id: string;
  conversationId: string;
  role: MessageRole;
  content: string;
  citations: SourceCitation[];
  processingTimeMs: number | null;
  tokenUsage: TokenUsage | null;
  createdAt: string;
}

export interface RagAnswerResponse {
  conversationId: string;
  messageId: string;
  answer: string;
  sources: SourceCitation[];
  processingTimeMs: number;
  tokenUsage: TokenUsage | null;
  grounded: boolean;
}

export interface UserDashboardSummary {
  totalDocuments: number;
  completedDocuments: number;
  processingDocuments: number;
  failedDocuments: number;
  totalConversations: number;
  recentDocuments: DocumentItem[];
}

export interface AdminDashboardSummary {
  totalUsers: number;
  totalDocuments: number;
  completedDocuments: number;
  processingDocuments: number;
  failedDocuments: number;
  totalConversations: number;
  totalAiRequests: number;
  failedAiRequests: number;
  totalTokensConsumed: number;
  activeModelInfo: Record<string, unknown>;
}

export interface AiAuditLogItem {
  id: string;
  userId: string | null;
  conversationId: string | null;
  documentId: string | null;
  operation: string;
  provider: string;
  model: string;
  promptTokens: number | null;
  completionTokens: number | null;
  totalTokens: number | null;
  durationMs: number;
  status: string;
  errorMessage: string | null;
  createdAt: string;
}

export interface ApiErrorPayload {
  timestamp?: string;
  status?: number;
  error?: string;
  code?: string;
  message?: string;
  validationErrors?: Record<string, string>;
}
