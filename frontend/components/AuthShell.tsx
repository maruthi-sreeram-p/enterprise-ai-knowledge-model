import { ArrowLeft } from "lucide-react";
import { StageLink } from "./AuthStage";
import { Logo } from "./ui";

/**
 * The compact card holding the sign-in or register form. Where it sits on the
 * screen, and the animated panel beside it, are handled by AuthStage.
 */
export default function AuthShell({
  title,
  subtitle,
  children,
  footer,
  wide = false,
}: {
  title: string;
  subtitle: string;
  children: React.ReactNode;
  footer: React.ReactNode;
  /** The register form has more fields, so it gets a wider card. */
  wide?: boolean;
}) {
  return (
    <main className="flex min-h-screen items-center justify-center px-5 py-8">
      <div
        className={`auth-card glass w-full p-6 ${
          wide ? "max-w-[28rem] sm:p-7" : "max-w-[22rem]"
        }`}
      >
        <div className="mb-4 flex items-center justify-between">
          <div className="flex items-center gap-2.5">
            <Logo size={28} />
            <p className="label !text-[0.6rem]">Knowledge Base</p>
          </div>
          <StageLink
            href="/welcome"
            className="label flex items-center gap-1 hover:text-ink"
          >
            <ArrowLeft className="h-3 w-3" />
            About
          </StageLink>
        </div>

        <h1 className="serif text-2xl font-extrabold italic leading-tight tracking-tight">
          {title}
        </h1>
        <p className="engraved mb-3 mt-0.5 text-xs">{subtitle}</p>
        <hr className="rule-gold mb-4" />

        {children}

        <div className="engraved mt-4 border-t border-line pt-3 text-xs">
          {footer}
        </div>
      </div>
    </main>
  );
}
