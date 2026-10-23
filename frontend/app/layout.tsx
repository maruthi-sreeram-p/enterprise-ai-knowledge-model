import type { Metadata, Viewport } from "next";
import "./globals.css";
import AuthProvider from "../components/AuthProvider";

export const metadata: Metadata = {
  title: "Enterprise AI Knowledge Base",
  description:
    "Ask questions about your organization's documents and get answers with sources.",
};

export const viewport: Viewport = {
  themeColor: "#101114",
  colorScheme: "dark",
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="en">
      <body>
        <AuthProvider>{children}</AuthProvider>
      </body>
    </html>
  );
}
