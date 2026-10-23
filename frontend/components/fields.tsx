"use client";

import { useId, useState } from "react";
import { Eye, EyeOff, TriangleAlert } from "lucide-react";

type Icon = React.ComponentType<{ className?: string }>;

interface TextFieldProps
  extends Omit<React.InputHTMLAttributes<HTMLInputElement>, "id"> {
  label: string;
  icon?: Icon;
  hint?: string;
}

/** A labelled input sunk into the panel, with an optional engraved icon. */
export function TextField({
  label,
  icon: Icon,
  hint,
  className = "",
  ...input
}: TextFieldProps) {
  const id = useId();
  return (
    <div>
      <label htmlFor={id} className="label mb-2 ml-1 block">
        {label}
      </label>
      <div className="relative">
        {Icon && (
          <Icon className="pointer-events-none absolute left-3.5 top-1/2 h-[18px] w-[18px] -translate-y-1/2 text-ink-dim" />
        )}
        <input
          id={id}
          {...input}
          className={`input ${Icon ? "pl-11" : ""} ${className}`}
        />
      </div>
      {hint && <p className="engraved mt-1.5 ml-1 text-xs">{hint}</p>}
    </div>
  );
}

interface PasswordFieldProps extends TextFieldProps {
  /** Shows a Caps Lock warning, which is the usual cause of "wrong password". */
  warnCapsLock?: boolean;
}

/** Password input with a reveal key and a Caps Lock indicator. */
export function PasswordField({
  label,
  icon: Icon,
  hint,
  warnCapsLock = true,
  className = "",
  onKeyUp,
  onBlur,
  ...input
}: PasswordFieldProps) {
  const id = useId();
  const [visible, setVisible] = useState(false);
  const [capsLock, setCapsLock] = useState(false);

  return (
    <div>
      <label htmlFor={id} className="label mb-2 ml-1 block">
        {label}
      </label>
      <div className="relative">
        {Icon && (
          <Icon className="pointer-events-none absolute left-3.5 top-1/2 h-[18px] w-[18px] -translate-y-1/2 text-ink-dim" />
        )}
        <input
          id={id}
          {...input}
          type={visible ? "text" : "password"}
          onKeyUp={(event) => {
            if (warnCapsLock) {
              setCapsLock(event.getModifierState("CapsLock"));
            }
            onKeyUp?.(event);
          }}
          onBlur={(event) => {
            setCapsLock(false);
            onBlur?.(event);
          }}
          className={`input pr-12 ${Icon ? "pl-11" : ""} ${className}`}
        />
        <button
          type="button"
          onClick={() => setVisible((previous) => !previous)}
          aria-label={visible ? "Hide password" : "Show password"}
          aria-pressed={visible}
          className="absolute right-2 top-1/2 -translate-y-1/2 rounded-lg p-2 text-ink-dim transition-colors hover:text-brass-hi focus-visible:outline-2 focus-visible:outline-brass-hi"
        >
          {visible ? (
            <EyeOff className="h-[18px] w-[18px]" />
          ) : (
            <Eye className="h-[18px] w-[18px]" />
          )}
        </button>
      </div>
      {capsLock && (
        <p className="mt-1.5 ml-1 flex items-center gap-1.5 text-xs text-led-amber">
          <TriangleAlert className="h-3.5 w-3.5" />
          Caps Lock is on
        </p>
      )}
      {hint && !capsLock && (
        <p className="engraved mt-1.5 ml-1 text-xs">{hint}</p>
      )}
    </div>
  );
}

/**
 * Four-segment LED strength meter. It is guidance only; the server enforces
 * the real rules.
 */
export function StrengthMeter({ password }: { password: string }) {
  const score = passwordScore(password);
  const labels = ["Too short", "Weak", "Fair", "Good", "Strong"];
  const colors = ["red", "red", "amber", "green", "green"] as const;
  const color = colors[score];

  return (
    <div className="flex items-center gap-3" aria-live="polite">
      <div className="flex gap-1.5">
        {[1, 2, 3, 4].map((segment) => (
          <span
            key={segment}
            className={`led led-${color} ${segment <= score ? "" : "led-off"}`}
            style={{ height: 8, width: 22, borderRadius: 3 }}
          />
        ))}
      </div>
      <span className="engraved text-xs">
        {password ? labels[score] : "Minimum 8 characters"}
      </span>
    </div>
  );
}

export function passwordScore(password: string): number {
  if (password.length < 8) return 0;
  let score = 1;
  if (password.length >= 12) score++;
  if (/[a-z]/.test(password) && /[A-Z]/.test(password) && /\d/.test(password)) {
    score++;
  }
  if (/[^A-Za-z0-9]/.test(password)) score++;
  return Math.min(score, 4);
}
