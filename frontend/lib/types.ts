// Types mirroring the Spring Boot API's DTOs. Kept in one file so a change to a
// backend response has a single place to be reflected here.

export type Role = "ADMIN" | "MANAGER" | "EMPLOYEE";

export type DocumentStatus = "PENDING" | "PROCESSING" | "PROCESSED" | "FAILED";

export type DocumentVisibility =
  | "ALL_EMPLOYEES"
  | "MANAGERS_AND_ADMINS"
  | "ADMINS_ONLY";

export interface User {
  id: string;
  tenantId: string;
  name: string;
  email: string;
  role: Role;
  enabled: boolean;
  createdAt: string | null;
}

export interface AuthResponse {
  accessToken: string;
  tokenType: string;
  expiresAt: string;
  expiresInSeconds: number;
  user: User;
  organizationName: string;
}

export interface KnowledgeDocument {
  id: string;
  fileName: string;
  contentType: string;
  sizeBytes: number;
  status: DocumentStatus;
  visibility: DocumentVisibility;
  chunkCount: number;
  /**
   * Page count, for formats that have pages.
   *
   * Optional as well as nullable: the API omits the field for DOCX and TXT,
   * so it arrives as undefined. Compare with `!= null`.
   */
  pageCount?: number | null;
  failureReason?: string;
  uploadedBy: string;
  uploadedByName?: string;
  createdAt: string;
  processedAt?: string;
}

export interface SourceCitation {
  reference: number;
  documentId: string;
  fileName: string;
  /**
   * The page the passage came from. Never guessed.
   *
   * Optional as well as nullable: the API omits the field entirely for formats
   * that have no pages, so it arrives as undefined rather than null. Compare it
   * with `!= null` to cover both.
   */
  pageNumber?: number | null;
  score?: number;
  excerpt?: string;
  cited: boolean;
}

export interface ChatResponse {
  conversationId: string;
  messageId: string;
  conversationTitle: string;
  answer: string;
  sources: SourceCitation[];
  /** False when nothing relevant was found and no model call was made. */
  grounded: boolean;
  createdAt: string;
}

export interface Conversation {
  id: string;
  title: string;
  createdAt: string;
  updatedAt: string;
}

export interface ChatMessage {
  id: string;
  role: "USER" | "ASSISTANT";
  content: string;
  sources?: SourceCitation[];
  createdAt: string;
}

export interface ConversationDetail {
  id: string;
  title: string;
  createdAt: string;
  updatedAt: string;
  messages: ChatMessage[];
}

export interface AuditLog {
  id: string;
  userId: string | null;
  userName: string | null;
  action: string;
  resourceId: string | null;
  metadata: string | null;
  ipAddress: string | null;
  createdAt: string;
}

export interface DashboardSummary {
  totalDocuments: number;
  processedDocuments: number;
  failedDocuments: number;
  totalUsers: number;
  myConversations: number;
  totalAiQueries: number;
}

/** Spring Data's page wrapper, as it appears over the wire. */
export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
  first: boolean;
  last: boolean;
}

/** The single error shape every failing endpoint returns. */
export interface ApiErrorBody {
  timestamp: string;
  status: number;
  code: string;
  message: string;
  path: string;
  fieldErrors?: Record<string, string[]>;
}
