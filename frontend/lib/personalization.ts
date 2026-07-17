export type Theme = 'light' | 'dark' | 'system';
export type Contrast = 'normal' | 'high';
export type Font = 'inter' | 'serif' | 'mono';
export type AccentColor = 'blue' | 'green' | 'purple' | 'red' | 'orange' | 'pink';

export const THEME_OPTIONS: { value: Theme; label: string }[] = [
  { value: 'light', label: 'Light' },
  { value: 'dark', label: 'Dark' },
  { value: 'system', label: 'Match device' },
];

export const CONTRAST_OPTIONS: { value: Contrast; label: string }[] = [
  { value: 'normal', label: 'Normal' },
  { value: 'high', label: 'High contrast' },
];

// cssVar matches the next/font `variable` names set on <html> in the root
// layout (frontend/app/layout.tsx) -- both this picker's live preview and the
// actual site body reference the same variable, so the preview never lies.
export const FONT_OPTIONS: { value: Font; label: string; cssVar: string }[] = [
  { value: 'inter', label: 'Sans', cssVar: 'var(--font-inter)' },
  { value: 'serif', label: 'Serif', cssVar: 'var(--font-serif)' },
  { value: 'mono', label: 'Mono', cssVar: 'var(--font-mono)' },
];

export const ACCENT_COLOR_OPTIONS: { value: AccentColor; label: string; swatch: string }[] = [
  { value: 'blue', label: 'Blue', swatch: '#2563eb' },
  { value: 'green', label: 'Green', swatch: '#16a34a' },
  { value: 'purple', label: 'Purple', swatch: '#9333ea' },
  { value: 'red', label: 'Red', swatch: '#dc2626' },
  { value: 'orange', label: 'Orange', swatch: '#ea580c' },
  { value: 'pink', label: 'Pink', swatch: '#db2777' },
];

// Full shade ramps used by the root layout to set CSS custom properties,
// which globals.css's override rules then substitute in place of the
// hardcoded .bg-blue-*/.text-blue-*/etc utility classes used throughout the
// app -- picked to roughly match Tailwind's own palette shades so the result
// looks like a deliberate theme, not a random recolor.
export const ACCENT_SHADES: Record<AccentColor, { 50: string; 500: string; 600: string; 700: string; 800: string }> = {
  blue:   { 50: '#eff6ff', 500: '#3b82f6', 600: '#2563eb', 700: '#1d4ed8', 800: '#1e40af' },
  green:  { 50: '#f0fdf4', 500: '#22c55e', 600: '#16a34a', 700: '#15803d', 800: '#166534' },
  purple: { 50: '#faf5ff', 500: '#a855f7', 600: '#9333ea', 700: '#7e22ce', 800: '#6b21a8' },
  red:    { 50: '#fef2f2', 500: '#ef4444', 600: '#dc2626', 700: '#b91c1c', 800: '#991b1b' },
  orange: { 50: '#fff7ed', 500: '#f97316', 600: '#ea580c', 700: '#c2410c', 800: '#9a3412' },
  pink:   { 50: '#fdf2f8', 500: '#ec4899', 600: '#db2777', 700: '#be185d', 800: '#9d174d' },
};

// The 600/700/800 shades above read fine as *button fills* on a dark page
// (they're always paired with white text, so contrast comes from the fill
// itself) but recede when used as *text directly on the dark background* --
// a dark saturated blue on dark navy just looks muted rather than like a
// highlight. text/textHover are a lighter tint of the same hue (Tailwind's
// own 400/300 steps), used only for on-dark text color -- see the
// [data-theme='dark'] overrides in globals.css.
export const ACCENT_DARK_TEXT: Record<AccentColor, { text: string; textHover: string }> = {
  blue:   { text: '#60a5fa', textHover: '#93c5fd' },
  green:  { text: '#4ade80', textHover: '#86efac' },
  purple: { text: '#c084fc', textHover: '#d8b4fe' },
  red:    { text: '#f87171', textHover: '#fca5a5' },
  orange: { text: '#fb923c', textHover: '#fdba74' },
  pink:   { text: '#f472b6', textHover: '#f9a8d4' },
};

export function accentCssVars(accent: AccentColor): Record<string, string> {
  const shades = ACCENT_SHADES[accent] ?? ACCENT_SHADES.blue;
  const darkText = ACCENT_DARK_TEXT[accent] ?? ACCENT_DARK_TEXT.blue;
  return {
    '--accent-50': shades[50],
    '--accent-500': shades[500],
    '--accent-600': shades[600],
    '--accent-700': shades[700],
    '--accent-800': shades[800],
    '--accent-text-dark': darkText.text,
    '--accent-text-dark-hover': darkText.textHover,
  };
}
