---
name: Payments Ledger
description: A live demo that lets visitors try to break a double-entry ledger.
colors:
  accent: "#5546e8"
  accent-dark: "#4436c9"
  accent-wash: "#eeecfd"
  ink: "#0b2545"
  muted: "#4f6079"
  line: "#e1e7ef"
  wash: "#f5f7fb"
  background: "#ffffff"
  paid: "#0b7a5b"
  refused: "#b3261e"
  refused-wash: "#fcebea"
  code-ground: "#0f1f38"
typography:
  display:
    fontFamily: "-apple-system, BlinkMacSystemFont, Segoe UI, Roboto, Helvetica Neue, Arial, sans-serif"
    fontSize: "clamp(2.5rem, 5.2vw, 4.25rem)"
    fontWeight: 700
    lineHeight: 1.04
    letterSpacing: "-0.035em"
  headline:
    fontFamily: "-apple-system, BlinkMacSystemFont, Segoe UI, Roboto, Helvetica Neue, Arial, sans-serif"
    fontSize: "clamp(1.625rem, 3vw, 2.125rem)"
    fontWeight: 700
    lineHeight: 1.15
    letterSpacing: "-0.025em"
  body:
    fontFamily: "-apple-system, BlinkMacSystemFont, Segoe UI, Roboto, Helvetica Neue, Arial, sans-serif"
    fontSize: "1.0625rem"
    fontWeight: 400
    lineHeight: 1.6
  code:
    fontFamily: "ui-monospace, SFMono-Regular, Menlo, Consolas, monospace"
    fontSize: "0.8125rem"
    lineHeight: 1.55
rounded:
  control: "999px"
  inset: "10px"
  card: "16px"
spacing:
  page-gutter: "32px"
  step: "56px"
components:
  button-primary:
    backgroundColor: "{colors.accent}"
    textColor: "{colors.background}"
    rounded: "{rounded.control}"
    padding: "9px 20px"
    height: "44px"
  button-primary-hover:
    backgroundColor: "{colors.accent-dark}"
  button-secondary:
    backgroundColor: "{colors.background}"
    textColor: "{colors.ink}"
    rounded: "{rounded.control}"
    padding: "9px 20px"
    height: "44px"
  note-pill:
    backgroundColor: "{colors.accent-wash}"
    textColor: "{colors.accent-dark}"
    rounded: "{rounded.control}"
---

# Design System: Payments Ledger

## Overview

**Creative North Star: "The Working Proof"**

A payments page in the familiar fintech style, played straight, at a standard meant to sit credibly beside Stripe and Wise. The convention is deliberate: the familiar frame lets the live results be the interesting thing. Nothing decorative competes with a real response the visitor just caused.

Plain English comes first, and the real requests and responses sit one click away. The page is calm at rest and has one moment of motion, when payments land in the race grid.

**Key Characteristics:**
- White ground, deep navy ink, one violet accent
- One elevated surface: the live ledger, pinned for the whole story
- Large, tightly set headlines and tabular figures
- Results stated in words, with a drawn icon

## Colors

A restrained palette: neutrals tinted toward navy, one accent for actions, and two colours that carry meaning.

### Primary
- **Signal Violet** (accent): the primary action in each step, links, and focus rings.
- **Deep Violet** (accent-dark): hover on primary actions, link text, and an amount that has money held against it.

### Neutral
- **Ledger Navy** (ink): headlines, body text and figures.
- **Slate Ink** (muted): supporting text and table headers.
- **Hairline** (line): every divider and quiet border.
- **Quiet Wash** (wash): result blocks, disabled controls and unfinished step numbers.

### Named Rules
**The Earned Colour Rule.** Green and red mean paid and refused, and nothing else.

**The Never Colour Alone Rule.** Every state pairs its colour with a drawn tick or cross, and a word nearby.

## Typography

**Display and Body Font:** the platform sans stack, chosen so the page stays one file with no external requests.
**Mono Font:** the platform mono stack, for real request and response data only.

**Character:** plain and confident. Hierarchy comes from size and weight, with no labels above headings.

### Hierarchy
- **Display**: the one page headline.
- **Headline**: step and closing headings.
- **Body**: explanations, held to 65ch.
- **Code**: request and response logs, and short transfer ids.

## Layout

Two columns on desktop, up to 1240px wide: the story on the left and a 400px ledger pinned on the right. At 960px and below it becomes one column, and the ledger turns into a compact strip pinned to the top of the screen. Steps are separated by hairlines and 56px of space (40px on phones), not placed in cards.

## Elevation & Depth

Flat by default, with one lifted object. Only the ledger card has a shadow, because it is the one thing that stays with you as the page scrolls.

### Shadow Vocabulary
- **Ledger lift** (`box-shadow: 0 18px 40px -18px rgba(11, 37, 69, .28), 0 4px 10px -4px rgba(11, 37, 69, .08)`): the ledger card only.

## Shapes

Fully rounded controls (999px), softly rounded inset blocks (10px) and one larger card radius (16px). Step markers are circles.

## Components

### Buttons
- **Shape:** fully rounded, at least 44px tall.
- **Primary:** Signal Violet with white text, used for the main action in a step.
- **Hover / Focus:** darkens to Deep Violet; focus shows a 3px violet ring.
- **Secondary:** white with a hairline border that darkens to navy on hover.
- **Disabled:** Quiet Wash with muted text. A short note below explains what to do first.

### Live ledger
One row per account with the balance large and right-aligned. "Available" appears only when it differs from the balance. Reconciliation sits at the bottom, always visible.

### Result block
Quiet Wash, one line per outcome, each starting with a drawn icon and a bold verdict. A refusal is written as the system working, not as a failure.

### Race grid
Twenty cells that fill in the order real responses return. This is the page's one authored motion: a 450ms scale-in with an exponential ease-out, removed for reduced motion.

## Do's and Don'ts

### Do:
- **Do** show only figures that came from an API response.
- **Do** use mono only for real technical data.
- **Do** keep "Fake money" visible on the page.

### Don't:
- **Don't** use a label above a heading.
- **Don't** use Unicode glyphs or emoji as icons; use the drawn icon set.
- **Don't** copy another company's logo, name or marks. The bar is their craft, not their identity.
