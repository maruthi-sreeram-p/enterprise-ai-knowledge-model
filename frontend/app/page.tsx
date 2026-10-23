"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import {
  Bot,
  MessageSquare,
  Plus,
  SendHorizontal,
  Trash2,
  User,
} from "lucide-react";
import AppShell from "../components/AppShell";
import RequireAuth from "../components/RequireAuth";
import SourceList from "../components/SourceList";
import { Alert, Led, Screws } from "../components/ui";
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
    api
      .get<Page<Conversation>>("/api/conversations", { params: { size: 50 } })
      .then((response) => setConversations(response.data.content))
      .catch((caught) => setError(errorMessage(caught)));
  }, []);

  // Starting a new chat clears the transcript itself (see
  // startNewConversation), so this only needs to load an existing one.
  useEffect(() => {
    if (!activeId) return;
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
    <div className="flex h-full min-h-0 gap-3">
      <aside className="surface hidden w-64 shrink-0 flex-col p-3 lg:flex">
        <button
          onClick={startNewConversation}
          className="btn btn-primary mb-4 w-full"
        >
          <Plus className="h-4 w-4" />
          New chat
        </button>

        <p className="label mb-2 px-1">History</p>
        <div className="well min-h-0 flex-1 space-y-1 overflow-y-auto p-2">
          {conversations.length === 0 && (
            <p className="engraved mt-8 px-2 text-center text-sm">
              No conversations yet
            </p>
          )}

          {conversations.map((conversation) => {
            const active = activeId === conversation.id;
            return (
              <div key={conversation.id} className="group relative">
                <button
                  onClick={() => setActiveId(conversation.id)}
                  aria-current={active ? "page" : undefined}
                  className="nav-key w-full pr-10 text-left"
                >
                  <MessageSquare className="h-4 w-4 shrink-0" />
                  <span className="truncate">{conversation.title}</span>
                </button>
                <button
                  onClick={(event) => deleteConversation(event, conversation.id)}
                  aria-label={`Delete ${conversation.title}`}
                  className="absolute right-1.5 top-1/2 -translate-y-1/2 rounded-md p-1.5 text-ink-dim opacity-0 transition hover:text-led-red focus-visible:opacity-100 group-hover:opacity-100"
                >
                  <Trash2 className="h-4 w-4" />
                </button>
              </div>
            );
          })}
        </div>
      </aside>

      <section className="surface relative flex min-w-0 flex-1 flex-col p-3">
        <Screws inset={7} />

        <div className="mb-3 flex items-center justify-between px-5 pt-2">
          <p className="label flex items-center gap-2">
            <Led color={thinking ? "amber" : "green"} pulse={thinking} />
            {thinking ? "Searching documents" : "Ready"}
          </p>
          <p className="label hidden sm:block">Answers cite their sources</p>
        </div>

        <div className="well display min-h-0 flex-1 overflow-y-auto">
          <div className="mx-auto w-full max-w-3xl space-y-6 p-4 md:p-8">
            {messages.length === 0 && !thinking && (
              <div className="rise mt-12 text-center">
                <div className="medallion mx-auto mb-5 h-16 w-16">
                  <Bot className="h-7 w-7" strokeWidth={2.2} />
                </div>
                <h2 className="embossed mb-1.5 text-lg font-semibold">
                  Ask about your organization&apos;s documents
                </h2>
                <p className="engraved mx-auto max-w-md text-sm">
                  Answers come only from documents your role can access, and
                  every answer shows where it came from.
                </p>
              </div>
            )}

            {messages.map((message) => (
              <div
                key={message.id}
                className={`rise flex gap-3 ${
                  message.role === "USER" ? "justify-end" : "justify-start"
                }`}
              >
                {message.role === "ASSISTANT" && (
                  <div className="medallion mt-1 h-9 w-9 shrink-0">
                    <Bot className="h-4 w-4" strokeWidth={2.4} />
                  </div>
                )}

                <div
                  className={`max-w-[88%] px-5 py-4 text-[0.95rem] leading-relaxed ${
                    message.role === "USER"
                      ? "leather rounded-tr-sm"
                      : "plate rounded-2xl rounded-tl-sm text-ink"
                  }`}
                >
                  <p className="relative whitespace-pre-wrap">
                    {message.content}
                  </p>
                  {message.role === "ASSISTANT" && message.sources && (
                    <SourceList sources={message.sources} />
                  )}
                </div>

                {message.role === "USER" && (
                  <div className="plate mt-1 grid h-9 w-9 shrink-0 place-items-center rounded-full text-xs font-bold text-ink">
                    {user?.name?.charAt(0).toUpperCase() ?? (
                      <User className="h-4 w-4" />
                    )}
                  </div>
                )}
              </div>
            ))}

            {thinking && (
              <div className="rise flex justify-start gap-3">
                <div className="medallion mt-1 h-9 w-9 shrink-0">
                  <Bot className="h-4 w-4" strokeWidth={2.4} />
                </div>
                <div className="plate flex items-center gap-2.5 rounded-2xl rounded-tl-sm px-5 py-4">
                  {[0, 1, 2].map((dot) => (
                    <span
                      key={dot}
                      className="led led-amber led-pulse"
                      style={{ animationDelay: `${dot * 0.2}s` }}
                    />
                  ))}
                </div>
              </div>
            )}

            <div ref={bottomRef} />
          </div>
        </div>

        <div className="px-1 pt-3">
          {error && (
            <div className="mb-3">
              <Alert>{error}</Alert>
            </div>
          )}

          <form onSubmit={handleSend} className="flex items-center gap-3">
            <input
              type="text"
              value={input}
              onChange={(event) => setInput(event.target.value)}
              disabled={thinking}
              maxLength={4000}
              aria-label="Your question"
              placeholder={
                thinking
                  ? "Searching your documents…"
                  : "Ask a question about your documents…"
              }
              className="input py-3.5"
            />
            <button
              type="submit"
              disabled={!input.trim() || thinking}
              aria-label="Send question"
              className="btn btn-primary shrink-0 py-3.5"
            >
              <SendHorizontal className="h-5 w-5" />
              <span className="hidden sm:inline">Send</span>
            </button>
          </form>

          <p className="engraved mt-3 text-center text-xs">
            Answers are generated from your documents. Check the cited sources
            before relying on anything important.
          </p>
        </div>
      </section>
    </div>
  );
}
