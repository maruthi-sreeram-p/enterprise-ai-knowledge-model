"use client";

import { useState, useEffect, useRef } from "react";
import { Send, Menu, Plus, MessageSquare, Bot, User, Settings, Trash2, LogIn, LogOut } from "lucide-react";
import { useRouter } from "next/navigation";
import { useSession, signOut } from "next-auth/react";

type Message = { role: "ai" | "user"; content: string; isNew?: boolean; };
type Chat = { id: string; title: string; messages: Message[]; };

const TypewriterMessage = ({ content, isNew }: { content: string; isNew?: boolean }) => {
  const [displayed, setDisplayed] = useState(isNew ? "" : content);

  useEffect(() => {
    if (!isNew) { setDisplayed(content); return; }
    let i = 0;
    const timer = setInterval(() => {
      setDisplayed(content.slice(0, i + 1));
      i++;
      if (i >= content.length) clearInterval(timer);
    }, 15);
    return () => clearInterval(timer);
  }, [content, isNew]);

  return (
    <span>
      {displayed}
      {isNew && displayed.length < content.length && (
        <span className="inline-block w-1.5 h-4 ml-1 bg-[#2563eb] animate-pulse align-middle" />
      )}
    </span>
  );
};

export default function ChatInterface() {
  const router = useRouter();
  const { data: session, status } = useSession();
  
  const [input, setInput] = useState("");
  const [chats, setChats] = useState<Chat[]>([]);
  const [activeChatId, setActiveChatId] = useState<string | null>(null);
  const [isMounted, setIsMounted] = useState(false);
  const [isThinking, setIsThinking] = useState(false);
  const [isMobileMenuOpen, setIsMobileMenuOpen] = useState(false);
  
  const messagesEndRef = useRef<HTMLDivElement>(null);
  
  const userStorageKey = session?.user?.email ? `enterprise_ai_chats_${session.user.email}` : null;

  useEffect(() => {
    setIsMounted(true);
  }, []);

  useEffect(() => {
    if (!isMounted || status === "loading") return;

    if (status === "unauthenticated") {
      setChats([]);
      setActiveChatId(null);
      return;
    }

    if (userStorageKey) {
      const savedChats = localStorage.getItem(userStorageKey);
      if (savedChats) {
        const parsedChats = JSON.parse(savedChats).map((chat: Chat) => ({
          ...chat, messages: chat.messages.map((m: Message) => ({ ...m, isNew: false }))
        }));
        setChats(parsedChats);
        if (parsedChats.length > 0) setActiveChatId(parsedChats[0].id);
        else createNewChat();
      } else {
        createNewChat();
      }
    }
  }, [isMounted, status, userStorageKey]);

  useEffect(() => {
    if (isMounted && userStorageKey && chats.length > 0) {
      localStorage.setItem(userStorageKey, JSON.stringify(chats));
    }
  }, [chats, isMounted, userStorageKey]);

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: "smooth" });
  }, [chats, activeChatId, isThinking]);

  const createNewChat = () => {
    const newChat: Chat = {
      id: Date.now().toString(),
      title: "New Conversation",
      messages: [{ role: "ai", content: `Hello ${session?.user?.name?.split(' ')[0] || ''}. I am your Enterprise AI assistant. What document or policy can I help you find today?`, isNew: true }],
    };
    setChats(prev => [newChat, ...prev]);
    setActiveChatId(newChat.id);
    setIsMobileMenuOpen(false);
  };

  const handleSend = (e: React.FormEvent) => {
    e.preventDefault();
    if (!input.trim() || !activeChatId) return;

    const userMessage = input.trim();
    setInput("");

    setChats(prevChats => prevChats.map(chat => {
      if (chat.id === activeChatId) {
        const newTitle = chat.messages.length === 1 ? userMessage.slice(0, 30) + "..." : chat.title;
        return { ...chat, title: newTitle, messages: [...chat.messages, { role: "user", content: userMessage, isNew: false }] };
      }
      return chat;
    }));

    setIsThinking(true);
    setTimeout(() => {
      setIsThinking(false);
      setChats(prevChats => prevChats.map(chat => {
        if (chat.id === activeChatId) {
          return {
            ...chat,
            messages: [...chat.messages, { role: "ai", content: "I have scanned the isolated organization knowledge base using the RAG pipeline. Based on the documentation, you can access those resources via the internal portal.", isNew: true }]
          };
        }
        return chat;
      }));
    }, 1500);
  };

  const deleteChat = (e: React.MouseEvent, idToDelete: string) => {
    e.stopPropagation();
    const updatedChats = chats.filter(chat => chat.id !== idToDelete);
    setChats(updatedChats);
    
    if (updatedChats.length === 0 && userStorageKey) {
       localStorage.removeItem(userStorageKey);
    }

    if (activeChatId === idToDelete) {
      setActiveChatId(updatedChats.length > 0 ? updatedChats[0].id : null);
      if (updatedChats.length === 0) createNewChat();
    }
  };

  if (!isMounted) return <div className="h-screen bg-[#0a0f1c]"></div>;

  const activeChat = chats.find(c => c.id === activeChatId);

  const SidebarContent = () => (
    <>
      <div className="p-4 border-b border-slate-700/50">
        <button onClick={createNewChat} disabled={!session} className="btn-primary w-full py-2.5 flex items-center justify-start gap-3 shadow-[0_0_15px_rgba(37,99,235,0.3)] disabled:opacity-50 disabled:cursor-not-allowed">
          <Plus className="w-4 h-4" />
          <span className="font-semibold">New Chat</span>
        </button>
      </div>
      
      <div className="flex-1 overflow-y-auto hide-scrollbar p-3 space-y-1 mt-2">
        <p className="text-xs font-semibold text-slate-500 uppercase tracking-wider mb-3 ml-2 mt-2">Chat History</p>
        {chats.map(chat => (
          <div key={chat.id} className="relative group">
            <button 
              onClick={() => { setActiveChatId(chat.id); setIsMobileMenuOpen(false); }}
              className={`w-full text-left p-3 rounded-lg text-sm transition-all flex items-center gap-3 border pr-10 ${
                activeChatId === chat.id 
                  ? "bg-slate-800/80 text-slate-200 border-slate-600 shadow-sm" 
                  : "hover:bg-slate-800/50 text-slate-400 hover:text-slate-200 border-transparent"
              }`}
            >
              <MessageSquare className={`w-4 h-4 flex-shrink-0 ${activeChatId === chat.id ? "text-[#2563eb]" : ""}`} />
              <span className="truncate">{chat.title}</span>
            </button>
            <button 
              onClick={(e) => deleteChat(e, chat.id)}
              className="absolute right-2 top-1/2 -translate-y-1/2 p-1.5 text-slate-500 hover:text-red-400 opacity-0 group-hover:opacity-100 transition-opacity rounded-md hover:bg-slate-700"
            >
              <Trash2 className="w-4 h-4" />
            </button>
          </div>
        ))}
        {chats.length === 0 && session && (
          <p className="text-sm text-slate-500 text-center mt-10">No recent chats</p>
        )}
        {!session && (
           <p className="text-sm text-slate-500 text-center mt-10 px-4">Log in to view your chat history.</p>
        )}
      </div>

      <div className="p-4 border-t border-slate-700/50 space-y-2">
        
        {session ? (
          <div className="flex items-center justify-between p-2 rounded-xl hover:bg-slate-800/50 transition-colors border border-transparent hover:border-slate-700 group cursor-pointer">
            <div className="flex items-center gap-3 overflow-hidden">
              <div className="w-9 h-9 flex-shrink-0 rounded-full overflow-hidden border border-slate-600 shadow-[0_0_10px_rgba(37,99,235,0.2)]">
                {session.user?.image ? (
                  <img src={session.user.image} alt="Profile" className="w-full h-full object-cover" />
                ) : (
                  <div className="w-full h-full bg-gradient-to-br from-[#2563eb] to-[#1d4ed8] flex items-center justify-center text-white font-bold text-sm">
                    {session.user?.name ? session.user.name.charAt(0).toUpperCase() : "U"}
                  </div>
                )}
              </div>
              <div className="flex flex-col truncate">
                <span className="text-sm font-medium text-slate-200 truncate">{session.user?.name}</span>
                <span className="text-xs text-slate-500 truncate">{session.user?.email}</span>
              </div>
            </div>
            
            <button 
              onClick={() => {
                setChats([]);
                signOut({ callbackUrl: "/login" });
              }}
              className="p-2 text-slate-500 hover:text-red-400 opacity-0 group-hover:opacity-100 transition-all rounded-md hover:bg-slate-700"
              title="Log Out"
            >
              <LogOut className="w-4 h-4" />
            </button>
          </div>
        ) : (
          <>
            <button className="w-full text-left p-3 rounded-lg hover:bg-slate-800/50 text-slate-400 hover:text-slate-200 text-sm transition-colors flex items-center gap-3">
              <Settings className="w-4 h-4" />
              <span>Settings</span>
            </button>
            <button 
              onClick={() => router.push('/login')}
              className="w-full text-left p-3 rounded-lg hover:bg-[#2563eb]/20 text-slate-300 hover:text-[#2563eb] text-sm transition-colors flex items-center gap-3 border border-transparent hover:border-[#2563eb]/50"
            >
              <LogIn className="w-4 h-4" />
              <span className="font-medium">Log In / Sign Up</span>
            </button>
          </>
        )}
      </div>
    </>
  );

  return (
    <div className="flex h-screen bg-[#0a0f1c] overflow-hidden relative">
      <aside className="w-64 glass-panel border-r border-slate-700/50 hidden md:flex flex-col rounded-none z-20">
        <SidebarContent />
      </aside>

      {isMobileMenuOpen && (
        <div className="fixed inset-0 z-40 bg-black/60 backdrop-blur-sm md:hidden" onClick={() => setIsMobileMenuOpen(false)} />
      )}
      <aside className={`fixed inset-y-0 left-0 z-50 w-72 glass-panel rounded-none border-r border-slate-700/50 transform transition-transform duration-300 ease-in-out md:hidden flex flex-col ${isMobileMenuOpen ? "translate-x-0" : "-translate-x-full"}`}>
        <SidebarContent />
      </aside>

      <main className="flex-1 flex flex-col relative w-full h-full">
        <header className="md:hidden glass-panel h-16 rounded-none border-b border-slate-700/50 flex items-center px-4 justify-between z-10 w-full">
          <button onClick={() => setIsMobileMenuOpen(true)} className="p-2 text-slate-300 hover:bg-slate-800 rounded-lg transition-colors">
            <Menu className="w-6 h-6" />
          </button>
          <span className="font-bold text-white tracking-tight">Enterprise AI</span>
          
          {session ? (
            <div className="w-8 h-8 rounded-full overflow-hidden border border-slate-600">
              {session.user?.image ? (
                <img src={session.user.image} alt="Profile" className="w-full h-full object-cover" />
              ) : (
                <div className="w-full h-full bg-[#2563eb] flex items-center justify-center text-white font-bold text-xs">
                  {session.user?.name ? session.user.name.charAt(0).toUpperCase() : "U"}
                </div>
              )}
            </div>
          ) : (
            <button onClick={() => router.push('/login')} className="p-2 text-[#2563eb] hover:bg-slate-800 rounded-lg">
              <LogIn className="w-5 h-5" />
            </button>
          )}
        </header>

        <div className="flex-1 overflow-y-auto hide-scrollbar p-4 md:p-8 w-full max-w-4xl mx-auto space-y-8 scroll-smooth">
          {activeChat?.messages.map((msg, idx) => (
            <div key={idx} className={`flex gap-4 ${msg.role === "user" ? "justify-end" : "justify-start"} animate-slide-up`}>
              {msg.role === "ai" && (
                <div className="w-8 h-8 md:w-10 md:h-10 rounded-full bg-[#2563eb]/20 border border-[#2563eb]/50 flex items-center justify-center flex-shrink-0 mt-1 shadow-[0_0_10px_rgba(37,99,235,0.2)]">
                  <Bot className="w-4 h-4 md:w-5 md:h-5 text-[#2563eb]" />
                </div>
              )}

              <div className={`p-4 md:p-5 rounded-2xl max-w-[85%] text-sm md:text-base leading-relaxed ${
                msg.role === "user" 
                  ? "bg-[#2563eb] text-white rounded-tr-sm shadow-[0_4px_20px_rgba(37,99,235,0.2)]" 
                  : "glass-panel rounded-tl-sm text-slate-200 border-slate-700/50"
              }`}>
                {msg.role === "ai" ? <TypewriterMessage content={msg.content} isNew={msg.isNew} /> : msg.content}
              </div>

              {msg.role === "user" && (
                <div className="w-8 h-8 md:w-10 md:h-10 rounded-full overflow-hidden bg-slate-700 border border-slate-600 flex items-center justify-center flex-shrink-0 mt-1 shadow-sm">
                  {session?.user?.image ? (
                    <img src={session.user.image} alt="User" className="w-full h-full object-cover" />
                  ) : session?.user?.name ? (
                     <span className="text-slate-200 font-bold text-xs">{session.user.name.charAt(0).toUpperCase()}</span>
                  ) : (
                     <User className="w-4 h-4 md:w-5 md:h-5 text-slate-300" />
                  )}
                </div>
              )}
            </div>
          ))}

          {isThinking && (
            <div className="flex gap-4 justify-start animate-slide-up">
              <div className="w-8 h-8 md:w-10 md:h-10 rounded-full bg-[#2563eb]/20 border border-[#2563eb]/50 flex items-center justify-center flex-shrink-0 mt-1 shadow-[0_0_10px_rgba(37,99,235,0.2)]">
                <Bot className="w-4 h-4 md:w-5 md:h-5 text-[#2563eb]" />
              </div>
              <div className="glass-panel rounded-2xl rounded-tl-sm px-5 py-4 flex items-center gap-2">
                <span className="w-2 h-2 bg-[#2563eb] rounded-full animate-bounce"></span>
                <span className="w-2 h-2 bg-[#2563eb] rounded-full animate-bounce" style={{ animationDelay: "0.2s" }}></span>
                <span className="w-2 h-2 bg-[#2563eb] rounded-full animate-bounce" style={{ animationDelay: "0.4s" }}></span>
              </div>
            </div>
          )}
          <div ref={messagesEndRef} className="h-32 md:h-40 flex-shrink-0 w-full" />
        </div>

        <div className="absolute bottom-0 left-0 w-full bg-gradient-to-t from-[#0a0f1c] via-[#0a0f1c] to-transparent pt-12 pb-6 px-4 md:px-8 z-10">
          <div className="max-w-4xl mx-auto relative group">
            <form onSubmit={handleSend} className="relative flex items-center shadow-glass rounded-2xl bg-[#111928]/95 backdrop-blur-xl border border-slate-700/80 transition-all focus-within:border-[#2563eb]/50 focus-within:shadow-[0_0_20px_rgba(37,99,235,0.15)]">
              <input
                type="text"
                value={input}
                onChange={(e) => setInput(e.target.value)}
                disabled={isThinking || !session}
                placeholder={!session ? "Please log in to ask a question..." : isThinking ? "AI is processing..." : "Ask a question or search documents..."}
                className="w-full bg-transparent pl-6 pr-16 py-4 md:py-5 focus:outline-none text-slate-200 placeholder-slate-500 text-sm md:text-base disabled:opacity-50"
              />
              <button type="submit" disabled={!input.trim() || isThinking || !session} className="absolute right-3 md:right-4 p-2 bg-[#2563eb] hover:bg-[#1d4ed8] disabled:bg-slate-800 disabled:text-slate-600 text-white rounded-lg transition-all shadow-md">
                <Send className="w-4 h-4 md:w-5 md:h-5" />
              </button>
            </form>
            <p className="text-center text-xs text-slate-500 mt-3 hidden md:block">
              AI-generated content may be inaccurate. Verify important information against official documents.
            </p>
          </div>
        </div>
      </main>
    </div>
  );
}