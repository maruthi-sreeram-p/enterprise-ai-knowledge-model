import AuthStage from "../../components/AuthStage";

export default function AuthLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return <AuthStage>{children}</AuthStage>;
}
