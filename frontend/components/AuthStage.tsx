"use client";

import {
  createContext,
  startTransition,
  useContext,
  useEffect,
  useRef,
  useState,
} from "react";
import { usePathname, useRouter } from "next/navigation";
import AuthArt from "./AuthArt";

type Mode = "welcome" | "login" | "register";

const CAPTIONS: Record<Mode, string> = {
  welcome: "Ask. Get cited answers. Stay private.",
  login: "Ask. Get cited answers. Stay private.",
  register: "Your team's knowledge, one question away.",
};

/** Where the 3D scene sits: opposite the card, which is right for sign-in. */
const FOCUS: Record<Mode, number> = { welcome: 0, login: -1, register: 1 };

/** How long the old screen takes to fade before the route actually changes. */
const EXIT_MS = 380;

function modeOf(path: string): Mode {
  if (path.startsWith("/login")) return "login";
  if (path.startsWith("/register")) return "register";
  return "welcome";
}

const StageContext = createContext<(href: string) => void>(() => {});

/**
 * The persistent frame around the welcome, sign-in and register screens.
 *
 * It lives in the route group's layout, so it survives navigation between
 * those three routes. That is what makes the transitions possible: the
 * full-screen animation is the same element throughout. It fades in when
 * leaving the welcome page, and its focal point glides to the side opposite
 * the glass card, while the old card drifts out and the new one drifts in.
 *
 * A click starts the panel moving and the old screen fading at once; the route
 * changes a moment later. Direct loads and the browser's back button just use
 * the URL, so they land in the right place without any choreography.
 */
export default function AuthStage({ children }: { children: React.ReactNode }) {
  const pathname = usePathname();
  const router = useRouter();
  const timer = useRef<ReturnType<typeof setTimeout> | null>(null);

  // A move that has started but whose route change has not landed yet. It
  // only applies while we are still on the page it started from.
  const [pending, setPending] = useState<{ from: string; mode: Mode } | null>(
    null,
  );

  const pageMode = modeOf(pathname);
  const leaving = pending !== null && pending.from === pathname;
  const panelMode = leaving ? pending.mode : pageMode;

  useEffect(() => {
    // Only Chromium renders an SVG filter inside backdrop-filter. Elsewhere the
    // glass stays a plain frosted pane rather than losing its blur entirely.
    const agent = navigator as Navigator & {
      userAgentData?: { brands: { brand: string }[] };
    };
    if (agent.userAgentData?.brands.some((entry) => entry.brand === "Chromium")) {
      document.documentElement.dataset.liquid = "";
    }

    router.prefetch("/welcome");
    router.prefetch("/login");
    router.prefetch("/register");
    return () => {
      if (timer.current) clearTimeout(timer.current);
    };
  }, [router]);

  const go = (href: string) => {
    const target = modeOf(href);
    if (target === pageMode || leaving) return;
    setPending({ from: pathname, mode: target });
    // Clearing `pending` in the same transition as the navigation means both
    // commit together once the new page is ready, so the panel never snaps
    // back, and returning to this page later (say with the back button) does
    // not find a stale pending move.
    timer.current = setTimeout(
      () =>
        startTransition(() => {
          setPending(null);
          router.push(href);
        }),
      EXIT_MS,
    );
  };

  return (
    <StageContext.Provider value={go}>
      {/* The water ripple for the glass cards: noise used as a displacement
          map, so whatever moves behind a card bends as if seen through water.
          The noise itself is static; animating it would regenerate it every
          frame, which is slow. */}
      <svg aria-hidden width="0" height="0" className="absolute">
        <filter
          id="water-glass"
          x="0%"
          y="0%"
          width="100%"
          height="100%"
          colorInterpolationFilters="sRGB"
        >
          <feTurbulence
            type="fractalNoise"
            baseFrequency="0.009 0.014"
            numOctaves={2}
            seed={4}
            result="noise"
          />
          <feGaussianBlur in="noise" stdDeviation="1.5" result="ripples" />
          <feDisplacementMap
            in="SourceGraphic"
            in2="ripples"
            scale={42}
            xChannelSelector="R"
            yChannelSelector="G"
          />
        </filter>
      </svg>

      <div className="stage-art" data-mode={panelMode}>
        <AuthArt
          caption={CAPTIONS[panelMode]}
          focus={FOCUS[panelMode]}
          active={panelMode !== "welcome"}
        />
      </div>

      <div
        key={pathname}
        className="stage-content"
        data-mode={pageMode}
        data-leaving={leaving ? pending.mode : undefined}
      >
        {children}
      </div>
    </StageContext.Provider>
  );
}

/**
 * A link between the welcome, sign-in and register screens. It is a real
 * anchor (so it can be opened in a new tab or read by a screen reader), but a
 * plain click is routed through the stage so the transition plays.
 */
export function StageLink({
  href,
  className,
  children,
}: {
  href: string;
  className?: string;
  children: React.ReactNode;
}) {
  const go = useContext(StageContext);
  return (
    <a
      href={href}
      className={className}
      onClick={(event) => {
        if (event.metaKey || event.ctrlKey || event.shiftKey || event.button !== 0) {
          return;
        }
        event.preventDefault();
        go(href);
      }}
    >
      {children}
    </a>
  );
}
