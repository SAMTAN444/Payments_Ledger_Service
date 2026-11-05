# Product

<!-- impeccable:product-schema 1 -->

## Platform

web

## Stack

One static HTML file with plain CSS and JavaScript at `src/main/resources/static/index.html`, served by the Spring Boot app at `/`. No build step, no external scripts, same origin as the API. Backend: Kotlin, Spring Boot 4, PostgreSQL.

## Users

- **Recruiters and hiring managers** skimming a portfolio link, usually for under a minute. They need to grasp in plain English what this does and why it is hard.
- **Backend engineers** evaluating the author, often in an interview. They want evidence: real requests, responses and status codes.

The page serves both in layers: plain English first, with the real technical detail one step away.

## Product Purpose

A double-entry payments ledger built by Samuel Tan as a learning and portfolio project. The page lets a visitor see the ledger stay correct under the conditions that break naive systems. Success: a visitor can say what happened and why it matters after following the story once.

## Positioning

Most portfolio projects show that features work. This one shows that money stays correct when things go wrong: retried requests, simultaneous requests, refunds, and held funds, with a reconciliation check that verifies the result against the actual database rows.

## Operating Context

A visitor arrives from a CV, a README link or a shared link, on a laptop or phone, with no account and no setup. The page runs against the live API, so every result shown is a real response.

## Capabilities and Constraints

- Format: a guided story, one scenario step by step, explaining each result.
- Demonstrates: retries (idempotency), races (concurrent transfers), holds, refunds and history. Account balances and the reconciliation result stay visible throughout.
- Amounts are integer cents in SGD. The API is the source of truth; the page never calculates a balance itself.
- There is no login, so anyone can move money. All money is fake and the page must say so.
- Not a banking app: no real customers, payment rails or production use.

## Brand Commitments

The owner chose the category-standard fintech look, played straight. The bar is sitting credibly alongside Stripe and Wise, without copying either one's name, logo or marks.

## Evidence on Hand

- The live API, its reconciliation endpoint and metrics.
- A real test suite with concurrency tests (see `README.md`).
- No testimonials, customers, usage numbers or performance benchmarks exist. Do not invent any.

## Product Principles

1. Show, don't claim: every statement on the page is backed by a real response the visitor just caused.
2. Plain English first, with the technical truth one step away.
3. Honest about limits: fake money, no login, learning project.
4. A failure the ledger refuses (a 422) is a success of the system, and reads that way.

## Accessibility & Inclusion

No product-specific requirement established beyond working keyboard use, readable contrast and phone-width layouts.
