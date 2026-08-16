---
name: CronoBus Dark Mode
colors:
  surface: '#141313'
  surface-dim: '#141313'
  surface-bright: '#3a3939'
  surface-container-lowest: '#0e0e0e'
  surface-container-low: '#1c1b1b'
  surface-container: '#201f1f'
  surface-container-high: '#2b2a2a'
  surface-container-highest: '#353434'
  on-surface: '#e5e2e1'
  on-surface-variant: '#c4c7c7'
  inverse-surface: '#e5e2e1'
  inverse-on-surface: '#313030'
  outline: '#8e9192'
  outline-variant: '#444748'
  surface-tint: '#c8c6c5'
  primary: '#c8c6c5'
  on-primary: '#313030'
  primary-container: '#121212'
  on-primary-container: '#7e7d7d'
  inverse-primary: '#5f5e5e'
  secondary: '#bbc8d0'
  on-secondary: '#263238'
  secondary-container: '#3c494f'
  on-secondary-container: '#aab7bf'
  tertiary: '#3ce36a'
  on-tertiary: '#003912'
  tertiary-container: '#001704'
  on-tertiary-container: '#00913a'
  error: '#ffb4ab'
  on-error: '#690005'
  error-container: '#93000a'
  on-error-container: '#ffdad6'
  primary-fixed: '#e5e2e1'
  primary-fixed-dim: '#c8c6c5'
  on-primary-fixed: '#1c1b1b'
  on-primary-fixed-variant: '#474646'
  secondary-fixed: '#d7e4ec'
  secondary-fixed-dim: '#bbc8d0'
  on-secondary-fixed: '#111d23'
  on-secondary-fixed-variant: '#3c494f'
  tertiary-fixed: '#69ff87'
  tertiary-fixed-dim: '#3ce36a'
  on-tertiary-fixed: '#002108'
  on-tertiary-fixed-variant: '#00531e'
  background: '#141313'
  on-background: '#e5e2e1'
  surface-variant: '#353434'
typography:
  display-lg:
    fontFamily: Archivo Narrow
    fontSize: 48px
    fontWeight: '700'
    lineHeight: 52px
    letterSpacing: -0.02em
  display-lg-mobile:
    fontFamily: Archivo Narrow
    fontSize: 32px
    fontWeight: '700'
    lineHeight: 36px
    letterSpacing: -0.01em
  headline-md:
    fontFamily: Archivo Narrow
    fontSize: 24px
    fontWeight: '600'
    lineHeight: 32px
    letterSpacing: 0.01em
  body-lg:
    fontFamily: Inter
    fontSize: 18px
    fontWeight: '400'
    lineHeight: 28px
  body-md:
    fontFamily: Inter
    fontSize: 16px
    fontWeight: '400'
    lineHeight: 24px
  label-caps:
    fontFamily: JetBrains Mono
    fontSize: 12px
    fontWeight: '500'
    lineHeight: 16px
    letterSpacing: 0.08em
  data-tabular:
    fontFamily: JetBrains Mono
    fontSize: 14px
    fontWeight: '400'
    lineHeight: 20px
rounded:
  sm: 0.125rem
  DEFAULT: 0.25rem
  md: 0.375rem
  lg: 0.5rem
  xl: 0.75rem
  full: 9999px
spacing:
  unit: 4px
  xs: 4px
  sm: 8px
  md: 16px
  lg: 24px
  xl: 40px
  gutter: 16px
  margin-mobile: 16px
  margin-desktop: 32px
---

## Brand & Style

The design system is a high-performance, dark-themed utility interface designed for urban transit and logistics management. It prioritizes rapid information processing and mission-critical readability in low-light environments. 

The aesthetic is **High-Contrast Utilitarian**, blending elements of **Minimalism** with **Modern Enterprise** structures. It evokes a sense of precision, reliability, and technical authority. The UI should feel like a specialized tool: lean, efficient, and devoid of unnecessary ornamentation. Motion should be swift and functional, reinforcing the real-time nature of the data.

The target audience consists of transit operators and power users who require immediate visual hierarchy to distinguish between status updates and navigational data.

## Colors

The palette is optimized for high-contrast visibility against a deep void background.

- **Fondo Primario (#121212):** Provides an expansive, distraction-free canvas that reduces eye strain and makes active elements pop.
- **Superficie/Contenedores (#263238):** A cool-toned slate used to define structural hierarchy and grouping without breaking the dark-mode immersion.
- **Éxito/Estado Positivo (#00C853):** A vibrant emerald green used for on-time arrivals, active routes, and completed tasks.
- **Advertencia/Pre-alerta (#FF6D00):** A high-visibility neon orange reserved for delays, traffic alerts, and system warnings.

Neutral scales should favor cool greys to maintain the technical "slate" feel of the interface.

## Typography

Typography is used as a structural element. 

- **Archivo Narrow** handles all major headings and display data, providing a condensed, impactful look that maximizes horizontal space—critical for timetable displays.
- **Inter** provides a highly legible, neutral base for standard interface text and descriptive content.
- **JetBrains Mono** is utilized for technical metadata, timestamps, and ID numbers, emphasizing the systematic nature of the design system.

All headings should be treated with tight tracking, while data labels use expanded tracking for clarity.

## Layout & Spacing

This design system utilizes a **Fixed Grid** model on desktop and a **Fluid** model on mobile. 

- **Grilla:** 12 columns for desktop (max-width 1440px), 8 columns for tablet, and 4 columns for mobile.
- **Rhythm:** An 8px base grid drives all spacing, with a 4px "half-step" for tight internal component padding (e.g., inside chips or input fields).
- **Density:** High-density layouts are preferred. Information should be packed efficiently, using borders rather than expansive whitespace to separate data points.

## Elevation & Depth

In this dark-mode environment, depth is achieved through **Tonal Layering** and **Low-Contrast Outlines**.

- **Level 0 (#121212):** Global background.
- **Level 1 (#263238):** Primary cards and containers.
- **Level 2 (#37474F):** Hover states, active tabs, or nested child elements.

Avoid shadows. Instead, use a 1px "Inner Stroke" or "Ghost Border" (White at 10% opacity) on containers to define edges against the black background. This maintains the flat, utilitarian look while ensuring structural clarity.

## Shapes

The shape language is **Soft** but disciplined. 

Small radius corners (4px to 8px) prevent the UI from feeling aggressive while maintaining the professional, industrial aesthetic. Buttons and interactive inputs use a standard 4px radius. Status indicators (chips) may use a slightly higher radius to distinguish them from structural elements, but never full pill shapes.

## Components

- **Botones:** Primary buttons use a high-contrast treatment (Emerald Green background with Black text). Ghost buttons use a Slate Gray stroke (#263238) with White text.
- **Fichas de Estado (Chips):** Compact, using "JetBrains Mono" for the label. The background is a 20% opacity version of the status color (e.g., Orange at 20%) with a solid 1px border of the same color.
- **Campos de Entrada (Inputs):** Dark Slate background (#263238) with a 1px border. Focus state triggers the Emerald Green border.
- **Tarjetas (Cards):** No shadows. Defined by the Slate background and a subtle 1px top-border to indicate "shelf" layering.
- **Tablas de Datos:** High-density. Rows alternate with a subtle background shift (Level 1 vs Level 2). Header text is always in `label-caps`.
- **Indicadores de Tiempo:** Real-time countdowns should use `data-tabular` to prevent text jumping during numerical changes.