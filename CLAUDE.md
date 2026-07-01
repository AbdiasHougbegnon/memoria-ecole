# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Repository state

This repository currently contains no source code — only `memoria-master-prompt.md`, the engineering charter for the Memoria project. There is no build system, package manager, linter, or test suite yet. When code is added, update this file with the actual commands (build/lint/test/run-single-test) and a real architecture section derived from the code itself, not just the spec.

Until then, treat `memoria-master-prompt.md` as the source of truth for all product and architecture decisions — read it in full before doing substantive design or implementation work. What follows is a distilled summary of its non-obvious, load-bearing rules.

## What Memoria is

A platform that records sessions (meetings, classes, conferences), then transcribes, diarizes, summarizes, and makes them semantically searchable over time, with minimal setup. Two products share one engine:

- **Memoria Entreprise** — meeting minutes, action-item tracking with human confirmation, historical decision search.
- **Memoria École** — course summaries, class "couloirs" (shared spaces per cohort), an interactive voice tutor (advanced phase).

## Non-negotiable architecture rules

These apply to any code written in this repo, from Phase 2 onward (Phase 1 = single dev on the core engine, honor them in spirit):

- One database per microservice. No service reads another service's database directly.
- All business events flow through Kafka.
- Controllers orchestrate; they never contain business logic.
- Clean Architecture: dependencies point toward the domain, never outward. The domain layer has zero dependency on Azure, Kafka, or any specific database — those are replaceable infrastructure details.
- No circular dependencies between services.
- All APIs are versioned from day one (`/api/v1/...`).
- New capabilities are built into the shared engine first, never duplicated per-product. Client-specific customization is configuration, never bespoke code in the engine.

## Engine vs. product layers

- **Engine (shared, product-agnostic)**: audio capture via 30s streaming chunks (zero data loss), real-time transcription + speaker diarization, post-session summaries, automatic topic-based session grouping via embeddings ("fils de mémoire"), semantic search returning exact passage + timestamp, multi-tenant data isolation, lightweight visual capture (photos/PDF slides, not continuous video).
- **Entreprise layer**: decisions, engagements/tasks, deadlines, owners, projects, clients.
- **École layer**: notions, définitions, exercices, matières, séances, promotions.

Never encode Entreprise/École-specific concepts into the engine layer.

## AI doctrine (hard requirement, not a preference)

- The AI is never the source of truth — the original transcript always is.
- Every summary must be traceable to the exact transcript passage that justifies it.
- Every extracted decision/action must be traceable to the original spoken sentences.
- Users can always drill down from summary → transcript → audio.
- No business-critical output is produced by AI without a link back to its source. Without this traceability, extracted data has no legal/institutional value — this is a sales condition for regulated clients (banks, universities), not a nice-to-have.

## Deployment model

Not full multi-tenant SaaS. Each client gets a **dedicated instance** (own deploy, own domain, e.g. `memoria.episen.fr`), customized lightly (name/logo/colors). Isolation comes from separate instances, not internal tenant partitioning. What matters technically is fast, automated, reproducible single-instance deployment (Docker + Terraform), not internal multi-tenant cloisoning — though the engine's data model should still support logical tenant isolation without rewrite, in case large accounts later need a shared instance.

## Stack

- **Frontend**: React + TypeScript + Tailwind, PWA, WebRTC/MediaStream for capture.
- **Backend**: Java Spring Boot microservices, REST/gRPC sync + Kafka async.
- **AI**: Azure Speech (transcription/diarization), Azure OpenAI (summaries/classification/embeddings/tutor), Azure AI Search (vector search), Azure Document Intelligence (PDF), Azure TTS (voice tutor).
- **Data**: PostgreSQL (relational), Cosmos DB (transcripts/vectors), Blob Storage (audio/files), Redis (active sessions, real-time).
- **Infra**: AKS, Docker, Terraform, GitHub Actions/Azure DevOps CI/CD, Entra ID (OAuth2/OIDC/JWT/RBAC), Key Vault, Prometheus + Grafana + OpenTelemetry.

## Resilience requirements

Capture must never depend on AI availability — recording always works, analysis can catch up later. Specifically design for: Azure Speech outage (buffer audio, backfill transcription), Kafka outage (local queue + replay), network drop mid-session (client-side chunk buffering + resume), browser/app close (resume from last state), server restart (no active session lost — persist state, don't rely on memory only).

## Cost discipline (Azure AI costs)

Cache embeddings (never recompute a known one), auto-delete temp files, cold-tier archive old audio, batch model calls, reuse existing summaries instead of regenerating, enforce per-tenant quotas, monitor cost per service/tenant with alerts. Cost per session should be a known, designed-for number, not a surprise.

## Working method

Design before code: business need → users/scenarios → business rules/edge cases/permissions → user journeys/data models/events/API/architecture → then implementation, tests, docs, deploy. Any decision that would be non-obvious in six months deserves a short ADR at the time it's made, not retroactively.

Build order follows the roadmap phases (noyau audio → intelligence post-session → mémoire & recherche → plateforme/multi-tenant → fonctionnalités avancées); each phase must end with something real and demoable end-to-end — don't build for months without a working demo.
