"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { Bot, MessageSquare, Plus, Send, Trash2, User } from "lucide-react";
import AppShell from "../components/AppShell";
import RequireAuth from "../components/RequireAuth";
import SourceList from "../components/SourceList";
import { useAuth } from "../components/AuthProvider";
import { api, errorMessage } from "../lib/api";
import type {
  ChatMessage,
  ChatResponse,
  Conversation,
  ConversationDetail,
  Page,
} from "../lib/types";

export default function ChatPage() {
  return (
    <RequireAuth>
      <AppShell>
        <ChatWorkspace />
      </AppShell>
    </RequireAuth>
  );
}

function ChatWorkspace() {
  const { user } = useAuth();

  const [conversations, setConversations] = useState<Conversation[]>([]);
  const [activeId, setActiveId] = useState<string | null>(null);
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [input, setInput] = useState("");
  const [thinking, setThinking] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const bottomRef = useRef<HTMLDivElement>(null);

  const loadConversations = useCallback(async () => {
    try {
      const response = await api.get<Page<Conversation>>("/api/conversations", {
        params: { size: 50 },
      });
      setConversations(response.data.content);
    } catch (caught) {
      setError(errorMessage(caught));
    }
  }, []);

  useEffect(() => {
    void loadConversations();
  }, [loadConversations]);

  useEffect(() => {
    if (!activeId) {
      setMessages([]);
      return;
    }
    api
      .get<ConversationDetail>(`/api/conversations/${activeId}`)
      .then((response) => setMessages(response.data.messages))
      .catch((caught) => setError(errorMessage(caught)));
  }, [activeId]);

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: "smooth" });
  }, [messages, thinking]);

  const handleSend = async (event: React.FormEvent) => {
    event.preventDefault();
    const question = input.trim();
    if (!question || thinking) return;

    setInput("");
    setError(null);

    // The question is shown immediately with a provisional id. The server's
    // stored copy replaces it when the conversation is reloaded.
    const optimistic: ChatMessage = {
      id: `pending-${Date.now()}`,
      role: "USER",
      content: question,
      createdAt: new Date().toISOString(),
    };
    setMessages((previous) => [...previous, optimistic]);
    setThinking(true);

    try {
      const response = await api.post<ChatResponse>("/api/chat", {
        conversationId: activeId,
        question,
      });

      const answer: ChatMessage = {
        id: response.data.messageId,
        role: "ASSISTANT",
        content: response.data.answer,
        sources: response.data.sources,
        createdAt: response.data.createdAt,
      };
      setMessages((previous) => [...previous, answer]);

      if (!activeId) {
        setActiveId(response.data.conversationId);
      }
      void loadConversations();
    } catch (caught) {
      setError(errorMessage(caught));
      // Roll the question back out of the transcript; it was never answered.
      setMessages((previous) =>
        previous.filter((message) => message.id !== optimistic.id),
      );
      setInput(question);
    } finally {
      setThinking(false);
    }
  };

  const startNewConversation = () => {
    setActiveId(null);
    setMessages([]);
    setError(null);
  };

  const deleteConversation = async (
    event: React.MouseEvent,
    conversationId: string,
  ) => {
    event.stopPropagation();
    try {
      await api.delete(`/api/conversations/${conversationId}`);
      setConversations((previous) =>
        previous.filter((conversation) => conversation.id !== conversationId),
      );
      if (activeId === conversationId) {
        startNewConversation();
      }
    } catch (caught) {
      setError(errorMessage(caught));
    }
  };

  return (
    <div className="flex h-full min-h-0">
      <aside className="hidden w-64 shrink-0 flex-col border-r border-slate-800 bg-[#0b1120] lg:flex">
        <div className="p-3">
          <button
            onClick={startNewConversation}
            className="btn-primary w-full justify-start gap-3 py-2.5"
          >
            <Plus className="h-4 w-4" />
            <span className="font-semibold">New chat</span>
          </button>
        </div>

        <div className="hide-scrollbar flex-1 space-y-1 overflow-y-auto p-3">
          <p className="mb-2 ml-2 text-xs font-semibold uppercase tracking-wider text-slate-500">
            History
          </p>

          {conversations.length === 0 && (
            <p className="mt-8 px-2 text-center text-sm text-slate-600">
              No conversations yet
            </p>
          )}

          {conversations.map((conversation) => (
            <div key={conversation.id} className="group relative">
              <button
                onClick={() => setActiveId(conversation.id)}
                className={`flex w-full items-center gap-3 rounded-lg border py-2.5 pl-3 pr-10 text-left text-sm transition-all ${
                  activeId === conversation.id
                    ? "border-slate-600 bg-slate-800/80 text-slate-200"
                    : "border-transparent text-slate-400 hover:bg-slate-800/50 hover:text-slate-200"
                }`}
              >
                <MessageSquare
                  className={`h-4 w-4 shrink-0 ${
                    activeId === conversation.id ? "text-[#2563eb]" : ""
                  }`}
                />
                <span className="truncate">{conversation.title}</span>
              </button>
              <button
                onClick={(event) => deleteConversation(event, conversation.id)}
                aria-label={`Delete ${conversation.title}`}
                className="absolute right-2 top-1/2 -translate-y-1/2 rounded-md p-1.5 text-slate-500 opacity-0 transition-opacity hover:bg-slate-700 hover:text-red-400 group-hover:opacity-100"
              >
                <Trash2 className="h-4 w-4" />
              </button>
            </div>
          ))}
        </div>
      </aside>

      <div className="flex min-w-0 flex-1 flex-col">
        <div className="hide-scrollbar mx-auto w-full max-w-3xl flex-1 space-y-6 overflow-y-auto p-4 md:p-8">
          {messages.length === 0 && !thinking && (
            <div className="mt-20 text-center">
              <div className="mx-auto mb-4 flex h-12 w-12 items-center justify-center rounded-full border border-[#2563eb]/50 bg-[#2563eb]/20">
                <Bot className="h-6 w-6 text-[#2563eb]" />
              </div>
              <h2 className="mb-1 text-lg font-semibold text-slate-200">
                Ask about your organization&apos;s documents
              </h2>
              <p className="text-sm text-slate-500">
                Answers come only from documents your role can access, and every
                answer shows where it came from.
              </p>
            </div>
          )}

          {messages.map((message) => (
            <div
              key={message.id}
              className={`animate-slide-up flex gap-4 ${
                message.role === "USER" ? "justify-end" : "justify-start"
              }`}
            >
              {message.role === "ASSISTANT" && (
                <div className="mt-1 flex h-9 w-9 shrink-0 items-center justify-center rounded-full border border-[#2563eb]/50 bg-[#2563eb]/20">
                  <Bot className="h-4 w-4 text-[#2563eb]" />
                </div>
              )}

              <div
                className={`max-w-[85%] rounded-2xl p-4 text-sm leading-relaxed md:text-base ${
                  message.role === "USER"
                    ? "rounded-tr-sm bg-[#2563eb] text-white"
                    : "glass-panel rounded-tl-sm border-slate-700/50 text-slate-200"
                }`}
              >
                <p className="whitespace-pre-wrap">{message.content}</p>
                {message.role === "ASSISTANT" && message.sources && (
                  <SourceList sources={message.sources} />
                )}
              </div>

              {message.role === "USER" && (
                <div className="mt-1 flex h-9 w-9 shrink-0 items-center justify-center rounded-full border border-slate-600 bg-slate-700 text-xs font-bold text-slate-200">
                  {user?.name?.charAt(0).toUpperCase() ?? (
                    <User className="h-4 w-4" />
                  )}
                </div>
              )}
            </div>
          ))}

          {thinking && (
            <div className="animate-slide-up flex justify-start gap-4">
              <div className="mt-1 flex h-9 w-9 shrink-0 items-center justify-center rounded-full border border-[#2563eb]/50 bg-[#2563eb]/20">
                <Bot className="h-4 w-4 text-[#2563eb]" />
              </div>
              <div className="glass-panel flex items-center gap-2 rounded-2xl rounded-tl-sm px-5 py-4">
                <span className="h-2 w-2 animate-bounce rounded-full bg-[#2563eb]" />
                <span
                  className="h-2 w-2 animate-bounce rounded-full bg-[#2563eb]"
                  style={{ animationDelay: "0.2s" }}
                />
                <span
                  className="h-2 w-2 animate-bounce rounded-full bg-[#2563eb]"
                  style={{ animationDelay: "0.4s" }}
                />
              </div>
            </div>
          )}

          <div ref={bottomRef} />
        </div>

        <div className="border-t border-slate-800 bg-[#0a0f1c] px-4 py-4 md:px-8">
          <div className="mx-auto max-w-3xl">
            {error && (
              <div
                role="alert"
                className="mb-3 rounded-lg border border-red-500/40 bg-red-500/10 px-4 py-2.5 text-sm text-red-300"
              >
                {error}
              </div>
            )}

            <form
              onSubmit={handleSend}
              className="relative flex items-center rounded-2xl border border-slate-700/80 bg-[#111928]/95 transition-all focus-within:border-[#2563eb]/50"
            >
              <input
                type="text"
                value={input}
                onChange={(event) => setInput(event.target.value)}
                disabled={thinking}
                placeholder={
                  thinking
                    ? "Searching your documents…"
                    : "Ask a question about your documents…"
                }
                className="w-full bg-transparent py-4 pl-6 pr-16 text-sm text-slate-200 placeholder-slate-500 focus:outline-none disabled:opacity-50 md:text-base"
              />
              <button
                type="submit"
                disabled={!input.trim() || thinking}
                aria-label="Send question"
                className="absolute right-3 rounded-lg bg-[#2563eb] p-2 text-white transition-all hover:bg-[#1d4ed8] disabled:bg-slate-800 disabled:text-slate-600"
              >
                <Send className="h-4 w-4 md:h-5 md:w-5" />
              </button>
            </form>

            <p className="mt-3 text-center text-xs text-slate-600">
              Answers are generated from your documents. Check the cited sources
              before relying on anything important.
            </p>
          </div>
        </div>
      </div>
    </div>
  );
}
