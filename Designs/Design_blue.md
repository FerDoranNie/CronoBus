---
name: Transito Pro
colors:
  surface: '#f9f9f9'
  surface-dim: '#dadada'
  surface-bright: '#f9f9f9'
  surface-container-lowest: '#ffffff'
  surface-container-low: '#f3f3f3'
  surface-container: '#eeeeee'
  surface-container-high: '#e8e8e8'
  surface-container-highest: '#e2e2e2'
  on-surface: '#1a1c1c'
  on-surface-variant: '#454652'
  inverse-surface: '#2f3131'
  inverse-on-surface: '#f1f1f1'
  outline: '#767683'
  outline-variant: '#c6c5d4'
  surface-tint: '#4c56af'
  primary: '#000666'
  on-primary: '#ffffff'
  primary-container: '#1a237e'
  on-primary-container: '#8690ee'
  inverse-primary: '#bdc2ff'
  secondary: '#705d00'
  on-secondary: '#ffffff'
  secondary-container: '#fdd400'
  on-secondary-container: '#6f5c00'
  tertiary: '#400000'
  on-tertiary: '#ffffff'
  tertiary-container: '#680000'
  on-tertiary-container: '#ff6551'
  error: '#ba1a1a'
  on-error: '#ffffff'
  error-container: '#ffdad6'
  on-error-container: '#93000a'
  primary-fixed: '#e0e0ff'
  primary-fixed-dim: '#bdc2ff'
  on-primary-fixed: '#000767'
  on-primary-fixed-variant: '#343d96'
  secondary-fixed: '#ffe170'
  secondary-fixed-dim: '#e9c400'
  on-secondary-fixed: '#221b00'
  on-secondary-fixed-variant: '#544600'
  tertiary-fixed: '#ffdad4'
  tertiary-fixed-dim: '#ffb4a8'
  on-tertiary-fixed: '#410000'
  on-tertiary-fixed-variant: '#930000'
  background: '#f9f9f9'
  on-background: '#1a1c1c'
  surface-variant: '#e2e2e2'
typography:
  display-lg:
    fontFamily: Inter
    fontSize: 48px
    fontWeight: '800'
    lineHeight: 56px
    letterSpacing: -0.02em
  display-lg-mobile:
    fontFamily: Inter
    fontSize: 32px
    fontWeight: '800'
    lineHeight: 40px
    letterSpacing: -0.02em
  headline-md:
    fontFamily: Inter
    fontSize: 24px
    fontWeight: '700'
    lineHeight: 32px
  body-lg:
    fontFamily: Inter
    fontSize: 18px
    fontWeight: '400'
    lineHeight: 28px
  body-sm:
    fontFamily: Inter
    fontSize: 14px
    fontWeight: '400'
    lineHeight: 20px
  label-caps:
    fontFamily: JetBrains Mono
    fontSize: 12px
    fontWeight: '700'
    lineHeight: 16px
    letterSpacing: 0.05em
  data-display:
    fontFamily: JetBrains Mono
    fontSize: 20px
    fontWeight: '600'
    lineHeight: 24px
rounded:
  sm: 0.125rem
  DEFAULT: 0.25rem
  md: 0.375rem
  lg: 0.5rem
  xl: 0.75rem
  full: 9999px
spacing:
  base: 8px
  gutter: 24px
  margin-mobile: 16px
  margin-desktop: 32px
  touch-target-min: 48px
---

## Brand & Style

The brand personality is **utilitarian, authoritative, and hyper-legible**. Designed for high-stakes public transport management in Mexico, the UI must function flawlessly in both high-glare outdoor environments and dimly lit control rooms.

The design style is a blend of **Modern Minimalism** and **Industrial Graphic Design**. It prioritizes information density and operational speed over decorative elements. The aesthetic draws inspiration from international transit signage: high-contrast color blocks, rigid alignments, and clear directional cues. The goal is to evoke a sense of reliability and public service excellence, ensuring that complex logistical data is parsed without cognitive friction.

## Colors

The palette is built on high-contrast accessibility standards to ensure visibility for dispatchers and field operators.

- **Primary (#1A237E):** Used for structural elements like top navigation bars, sidebars, and header backgrounds. It establishes the "Institutional" anchor of the platform.
- **Secondary/Action (#FFD600):** A "Safety Yellow" reserved for the most critical user actions and highlight data points. It must always be paired with dark text (Navy or Black) for WCAG compliance.
- **Accents/Alerts (#D50000):** Used strictly for system errors, late arrivals, or emergency stop notifications.
- **Surface & Backgrounds:** Use `#FFFFFF` for data cards and `#F5F5F5` for the base canvas to reduce eye strain during long shifts.

## Typography

This design system utilizes **Inter** for all UI and instructional text due to its exceptional legibility and tall x-height. For technical data—such as vehicle IDs, timestamps, and GPS coordinates—**JetBrains Mono** is used to ensure individual characters are distinct and columns align perfectly in data-heavy views.

Headlines should be bold and direct. All labels for status indicators and data headers should be in uppercase using the `label-caps` style to differentiate them from interactive body text.

## Layout & Spacing

The system uses a **Fluid Grid** with fixed-width sidebars for desktop (280px). 

- **Desktop:** 12-column grid with 24px gutters. Use wide margins (32px) to prevent data from feeling cramped.
- **Mobile:** 4-column grid with 16px margins. 
- **Rhythm:** All spacing must be a multiple of 8px. This "8px Square" philosophy ensures that even dense tables remain breathable and easy to scan.
- **Touch Targets:** For the mobile interface used by drivers or field staff, no interactive element should be smaller than 48x48px to accommodate use in vibrating environments (moving vehicles).

## Elevation & Depth

To maintain a utilitarian and high-contrast feel, this design system avoids complex shadows and blurs.

- **Flat Layering:** Most surfaces are flat with 1px solid borders (`#E0E0E0`).
- **Low-Contrast Outlines:** Use subtle borders to define card boundaries on the `#F5F5F5` background.
- **Active Elevation:** Only the primary action buttons and urgent alert modals use a "Hard Shadow" (2px offset, 0 blur, 10% opacity black) to simulate a physical push-button feel without sacrificing the clean, modern aesthetic.
- **Z-Index Hierarchy:** Navigation (highest) > Modals > Floating Action Buttons > Content Cards.

## Shapes

The shape language is **Soft (0.25rem)**. This provides a modern touch while maintaining the structural rigidity expected of an industrial platform. 

- **Standard Elements:** Inputs, buttons, and small cards use a 4px (0.25rem) radius.
- **Large Containers:** Dashboard widgets and main content panels use 8px (0.5rem) to slightly soften the large layout blocks.
- **Data Tags:** Status chips (e.g., "En Camino") use a 2px radius to look more like physical stamped labels.

## Components

- **Buttons:** Primary actions use the `#FFD600` background with Navy text. They must include a clear hover state that darkens the yellow slightly. Secondary buttons use a Navy outline with no fill.
- **Inputs:** High-contrast borders (1.5px). Focused states must use a thick 3px Navy outline to ensure users know exactly where the cursor is.
- **Status Chips:** Use semantic coloring. Green for "On Time," Yellow for "Delayed," and Red for "Critical." These should use the `label-caps` typography.
- **Data Widgets:** Card-based components that summarize specific routes or vehicle health. They should feature a large "Display" metric (e.g., % of Fleet Active) using the secondary color as a highlight.
- **Navigation:** A persistent sidebar on desktop using the Primary Navy color, with icons clearly labeled in Spanish. On mobile, a bottom navigation bar for primary modules (Mapa, Flota, Alertas).
- **Tables:** Zebra-striping is mandatory for rows using `#FAFAFA` and `#FFFFFF` to assist in horizontal scanning of long data sets.