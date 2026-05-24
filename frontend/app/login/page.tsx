"use client";

import { Mail, Lock, ArrowLeft } from "lucide-react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { signIn } from "next-auth/react";

export default function LoginPage() {
  const router = useRouter();

  const handleLoginSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    console.log("Logging in...");
    router.push("/"); 
  };

  return (
    <main className="flex min-h-screen items-center justify-center p-4 relative bg-[#0a0f1c] overflow-hidden">
      <div className="absolute top-1/4 left-1/4 w-[500px] h-[500px] bg-[#2563eb]/20 rounded-full blur-[120px] animate-pulse-glow pointer-events-none"></div>
      <div className="absolute bottom-1/4 right-1/4 w-[400px] h-[400px] bg-blue-900/20 rounded-full blur-[100px] animate-pulse-glow pointer-events-none" style={{ animationDelay: '1s' }}></div>
      
      <div className="glass-panel p-8 md:p-10 w-full max-w-md relative z-10 animate-slide-up shadow-[0_0_50px_rgba(37,99,235,0.1)] border-slate-700/50">
        
        <button onClick={() => router.push('/')} className="absolute top-6 left-6 text-slate-400 hover:text-white transition-colors flex items-center gap-2 text-sm font-medium">
          <ArrowLeft className="w-4 h-4" />
        </button>

        <div className="text-center mb-8 mt-6">
          <h1 className="text-2xl font-bold tracking-tight mb-2 text-white">Welcome Back</h1>
          <p className="text-sm text-slate-400">Sign in to your Enterprise AI account</p>
        </div>

        <form onSubmit={handleLoginSubmit} className="space-y-5">
          <div>
            <label className="text-sm font-medium text-slate-300 ml-1 mb-1 block">Email Address</label>
            <div className="relative">
              <Mail className="absolute left-3 top-1/2 -translate-y-1/2 w-5 h-5 text-slate-500" />
              <input type="email" required placeholder="name@company.com" className="w-full pl-10 pr-4 py-3 bg-slate-900/50 border border-slate-600 rounded-lg focus:outline-none focus:ring-2 focus:ring-[#2563eb] text-slate-200 placeholder-slate-500 transition-all" />
            </div>
          </div>

          <div>
            <label className="text-sm font-medium text-slate-300 ml-1 mb-1 block">Password</label>
            <div className="relative">
              <Lock className="absolute left-3 top-1/2 -translate-y-1/2 w-5 h-5 text-slate-500" />
              <input type="password" required placeholder="••••••••" className="w-full pl-10 pr-4 py-3 bg-slate-900/50 border border-slate-600 rounded-lg focus:outline-none focus:ring-2 focus:ring-[#2563eb] text-slate-200 placeholder-slate-500 transition-all" />
            </div>
          </div>

          <button type="submit" className="btn-primary w-full mt-4 py-3 shadow-[0_0_15px_rgba(37,99,235,0.3)]">
            Sign In
          </button>
        </form>

        <div className="relative flex items-center justify-center my-6">
          <div className="border-t border-slate-700 w-full"></div>
          <span className="bg-[#111928] px-3 text-xs text-slate-400 absolute">OR</span>
        </div>

        <button 
          type="button"
          onClick={() => signIn("google", { callbackUrl: "/" })}
          className="w-full px-4 py-3 bg-slate-800/80 hover:bg-slate-700 border border-slate-600 text-white rounded-lg transition-all duration-200 flex items-center justify-center gap-3 font-medium"
        >
          <svg className="w-5 h-5" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
            <path d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09z" fill="#4285F4"/>
            <path d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.16v2.84C3.99 20.53 7.7 23 12 23z" fill="#34A853"/>
            <path d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.07H2.16C1.43 8.55 1 10.22 1 12s.43 3.45 1.16 4.93l3.68-2.84z" fill="#FBBC05"/>
            <path d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.16 7.07l3.68 2.84c.87-2.6 3.3-4.53 6.16-4.53z" fill="#EA4335"/>
          </svg>
          Continue with Google
        </button>

        <p className="text-center mt-8 text-sm text-slate-400">
          Don&apos;t have an account? <Link href="/register" className="text-[#2563eb] hover:text-[#1d4ed8] font-medium transition-colors">Sign up</Link>
        </p>
      </div>
    </main>
  );
}