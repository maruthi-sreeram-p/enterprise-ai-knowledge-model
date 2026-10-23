"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { Plus, SendHorizontal, Trash2 } from "lucide-react";
import AppShell from "../components/AppShell";
import RequireAuth from "../components/RequireAuth";
import SourceList from "../components/SourceList";
import { Alert } from "../components/ui";
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
    <div className="mx-auto flex h-full min-h-0 max-w-7xl">
      <aside className="hidden w-64 shrink-0 flex-col border-r border-line py-6 lg:flex">
        <div className="px-5">
          <button
            onClick={startNewConversation}
            className="btn btn-primary w-full"
          >
            <Plus className="h-4 w-4" />
            New question
          </button>
        </div>

        <p className="label mb-2 mt-7 px-5">Earlier</p>
        <div className="min-h-0 flex-1 overflow-y-auto">
          {conversations.length === 0 && (
            <p className="engraved px-5 py-3 text-sm">No conversations yet</p>
          )}

          {conversations.map((conversation) => {
            const active = activeId === conversation.id;
            return (
              <div key={conversation.id} className="group relative">
                <button
                  onClick={() => setActiveId(conversation.id)}
                  aria-current={active ? "page" : undefined}
                  className="nav-row truncate pr-10"
                >
                  {conversation.title}
                </button>
                <button
                  onClick={(event) => deleteConversation(event, conversation.id)}
                  aria-label={`Delete ${conversation.title}`}
                  className="absolute right-2 top-1/2 -translate-y-1/2 p-1.5 text-ink-dim opacity-0 transition hover:text-led-red focus-visible:opacity-100 group-hover:opacity-100"
                >
                  <Trash2 className="h-4 w-4" />
                </button>
              </div>
            );
          })}
        </div>
      </aside>

      <section className="flex min-w-0 flex-1 flex-col">
        <div className="min-h-0 flex-1 overflow-y-auto">
          <div className="mx-auto w-full max-w-3xl space-y-10 px-5 py-10 sm:px-8">
            {messages.length === 0 && !thinking && (
              <div className="rise pt-6">
                <p className="eyebrow mb-4">Ask</p>
                <h1 className="serif text-5xl font-extrabold italic leading-none tracking-tight">
                  What would you like to know?
                </h1>
                <p className="engraved mt-4 max-w-md text-sm leading-relaxed">
                  Answers come only from documents your role can access, and
                  every answer shows the passages it was drawn from.
                </p>
              </div>
            )}

            {messages.map((message) =>
              message.role === "USER" ? (
                <div key={message.id} className="rise flex justify-end">
                  <p className="leather serif max-w-[85%] whitespace-pre-wrap text-xl italic leading-snug">
                    {message.content}
                  </p>
                </div>
              ) : (
                <article key={message.id} className="rise">
                  <p className="eyebrow mb-3">Answer</p>
                  <div className="serif whitespace-pre-wrap text-[1.08rem] leading-[1.75]">
                    {message.content}
                  </div>
                  {message.sources && <SourceList sources={message.sources} />}
                </article>
              ),
            )}

            {thinking && (
              <div className="rise" role="status">
                <p className="eyebrow mb-3 flex items-center gap-3">
                  Searching your documents
                  <span className="flex gap-1.5">
                    {[0, 1, 2].map((dot) => (
                      <span
                        key={dot}
                        className="led led-amber led-pulse"
                        style={{ animationDelay: `${dot * 0.2}s` }}
                      />
                    ))}
                  </span>
                </p>
              </div>
            )}

            <div ref={bottomRef} />
          </div>
        </div>

        <div className="border-t border-line px-5 py-5 sm:px-8">
          <div className="mx-auto max-w-3xl">
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
                <SendHorizontal className="h-4 w-4" />
                <span className="hidden sm:inline">Ask</span>
              </button>
            </form>

            <p className="engraved mt-3 text-xs">
              Answers are generated from your documents. Check the cited
              sources before relying on anything important.
            </p>
          </div>
        </div>
      </section>
    </div>
  );
}
