---
name: Classic Signal
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
  on-surface-variant: '#444748'
  inverse-surface: '#2f3131'
  inverse-on-surface: '#f1f1f1'
  outline: '#747878'
  outline-variant: '#c4c7c7'
  surface-tint: '#5f5e5e'
  primary: '#0a0a0a'
  on-primary: '#ffffff'
  primary-container: '#212121'
  on-primary-container: '#898888'
  inverse-primary: '#c8c6c5'
  secondary: '#0b6c4b'
  on-secondary: '#ffffff'
  secondary-container: '#a0f4ca'
  on-secondary-container: '#177251'
  tertiary: '#110900'
  on-tertiary: '#ffffff'
  tertiary-container: '#2f1e00'
  on-tertiary-container: '#b57e00'
  error: '#ba1a1a'
  on-error: '#ffffff'
  error-container: '#ffdad6'
  on-error-container: '#93000a'
  primary-fixed: '#e5e2e1'
  primary-fixed-dim: '#c8c6c5'
  on-primary-fixed: '#1b1c1c'
  on-primary-fixed-variant: '#474746'
  secondary-fixed: '#a0f4ca'
  secondary-fixed-dim: '#84d7af'
  on-secondary-fixed: '#002114'
  on-secondary-fixed-variant: '#005137'
  tertiary-fixed: '#ffdeac'
  tertiary-fixed-dim: '#ffba38'
  on-tertiary-fixed: '#281900'
  on-tertiary-fixed-variant: '#604100'
  background: '#f9f9f9'
  on-background: '#1a1c1c'
  surface-variant: '#e2e2e2'
typography:
  display-lg:
    fontFamily: Inter
    fontSize: 48px
    fontWeight: '700'
    lineHeight: 56px
    letterSpacing: -0.02em
  headline-lg:
    fontFamily: Inter
    fontSize: 32px
    fontWeight: '600'
    lineHeight: 40px
    letterSpacing: -0.01em
  headline-lg-mobile:
    fontFamily: Inter
    fontSize: 24px
    fontWeight: '600'
    lineHeight: 32px
  headline-md:
    fontFamily: Inter
    fontSize: 24px
    fontWeight: '600'
    lineHeight: 32px
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
  body-sm:
    fontFamily: Inter
    fontSize: 14px
    fontWeight: '400'
    lineHeight: 20px
  label-bold:
    fontFamily: Inter
    fontSize: 12px
    fontWeight: '700'
    lineHeight: 16px
    letterSpacing: 0.05em
  label-md:
    fontFamily: Inter
    fontSize: 12px
    fontWeight: '500'
    lineHeight: 16px
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
  xl: 32px
  gutter: 20px
  margin-mobile: 16px
  margin-desktop: 32px
---

## Brand & Style
The design system is built on a "Classic Signal" aesthetic, prioritizing utility, clarity, and authoritative movement. It targets professionals in logistics, transit, and urban planning who require immediate information processing under high-cognitive-load environments. 

The style is **Modern Professional** with a **Minimalist/Utilitarian** lean. It draws inspiration from international road signage and high-density SaaS dashboards, utilizing stark contrasts, generous negative space, and rigid structural alignment. The emotional response is one of reliability, precision, and safety. Every visual element exists to serve a functional purpose; decoration is discarded in favor of clear information hierarchy.

## Colors
This design system utilizes a high-contrast palette to ensure accessibility and rapid identification.

- **Primary (Asphalt Black):** Used for primary text, structural borders, and navigation frames. It provides the grounding weight of the UI.
- **Secondary (Signal Green):** Reserved for positive actions (Go), active transit status, geofence boundaries, and primary interactive buttons.
- **Accent (Amber):** Exclusively for warnings, maintenance alerts, and traffic disruptions. It should be used sparingly to maintain its signaling power.
- **Surface:** Pure White (#FFFFFF) is the standard canvas, while Neutral (#F5F5F5) is used for "off-road" areas like sidebars and disabled states to reduce eye strain.

## Typography
Inter is selected for its exceptional legibility in data-heavy contexts and its neutral, systematic character. 

- **Headlines:** Use Semi-Bold to Bold weights with slight negative letter-spacing to mimic the density of official signage.
- **Body:** Standardized on a 16px base for optimal readability. Use `body-sm` for secondary data points and metadata.
- **Labels:** Small caps or bold uppercase labels should be used for status indicators and table headers to differentiate them from interactive data.
- **Hierarchy:** Ensure a clear vertical rhythm. Mobile views should aggressively downscale headlines to ensure map visibility remains prioritized.

## Layout & Spacing
The layout follows a **Fluid Grid** model with a hard 4px baseline shift to ensure all elements align to a predictable "road-like" structure.

- **Grid:** Use a 12-column grid for desktop. For data-intensive dashboards, use a "Dashboard Split" where a 320px fixed sidebar contains controls and a fluid right pane contains maps or data tables.
- **Gutters:** Maintain a strict 20px gutter between columns to prevent visual crowding of data.
- **Safe Areas:** On mobile, use 16px side margins. On desktop, expand to 32px to provide breathing room.
- **Density:** This design system supports a "Compact" mode for data grids, reducing the `md` spacing to `sm` (8px) to maximize information density.

## Elevation & Depth
In line with the utilitarian aesthetic, this design system avoids soft shadows and "fluff." Depth is communicated through **Tonal Layers** and **Low-Contrast Outlines**.

- **Surfaces:** Use 1px solid borders (`#E0E0E0`) to define containers. Do not use drop shadows for static cards.
- **Elevated States:** Only modals, dropdown menus, and active "floating" map controls may use a shadow. If used, it must be a "Hard Shadow": 4px offset, 0px blur, 10% opacity Asphalt Black.
- **Interactive Depth:** Buttons and interactive cards should feel "pressed" rather than "lifted." Use a 1px inset border or a subtle fill change (`Neutral-100`) on hover.

## Shapes
The shape language is **Soft (Level 1)**. 

Elements use a 0.25rem (4px) corner radius. This provides a professional, modern feel that is more approachable than sharp 0px corners, yet retains a disciplined, "engineered" look suitable for transit software. 

- **Buttons/Inputs:** 4px radius.
- **Cards/Containers:** 8px radius (`rounded-lg`).
- **Status Pills:** 12px radius (`rounded-xl`) to distinguish them from structural elements.

## Components
- **Buttons:** 
  - *Primary:* Signal Green fill with White text. Bold weight.
  - *Secondary:* Asphalt Black outline (1px) with Black text. 
  - *Warning:* Amber fill with Asphalt Black text for high-urgency alerts.
- **Input Fields:** 1px Asphalt Black border. Labels must always be visible above the field (no floating labels). Active state uses a 2px Signal Green bottom border.
- **Chips/Status:** Used for transit line numbers or status. Use high-contrast background fills with White or Black text based on the Signal/Amber logic.
- **Data Tables:** Horizontal borders only. No vertical lines. Row hover state uses Neutral (#F5F5F5).
- **Cards:** White background, 1px Gray-300 border, no shadow. 
- **Map Controls:** Grouped in a floating vertical stack with a 1px border and 4px radius. Use monochrome icons (Asphalt Black).