import { Led, Logo, Screws } from "./ui";

/**
 * The frame shared by sign-in and registration: a bolted metal faceplate with a
 * brass medallion and a status lamp, centred on the carbon backdrop.
 */
export default function AuthShell({
  title,
  subtitle,
  children,
  footer,
}: {
  title: string;
  subtitle: string;
  children: React.ReactNode;
  footer: React.ReactNode;
}) {
  return (
    <main className="relative flex min-h-screen items-center justify-center overflow-hidden p-4 py-10">
      {/* Warm pool of light on the desk behind the panel. */}
      <div
        aria-hidden
        className="pointer-events-none absolute left-1/2 top-1/2 h-[620px] w-[620px] -translate-x-1/2 -translate-y-1/2 rounded-full"
        style={{
          background:
            "radial-gradient(circle, rgba(212,162,76,0.13), transparent 65%)",
        }}
      />

      <div className="surface rise relative z-10 w-full max-w-[26rem] px-8 pb-8 pt-10 sm:px-10">
        <Screws />

        <div className="mb-8 flex flex-col items-center text-center">
          <Logo size={64} />
          <p className="label mt-5 flex items-center gap-2">
            <Led color="green" pulse />
            Enterprise AI Knowledge Base
          </p>
          <h1 className="embossed mt-3 text-2xl font-bold tracking-tight">
            {title}
          </h1>
          <p className="engraved mt-1.5 text-sm">{subtitle}</p>
        </div>

        {children}

        <div className="engraved mt-8 border-t border-black/50 pt-5 text-center text-sm shadow-[0_-1px_0_rgba(255,255,255,0.05)]">
          {footer}
        </div>
      </div>
    </main>
  );
}
