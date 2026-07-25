# ADR-0007: Android architecture, dependency injection, and HTTP client

- **Status:** ✅ **Accepted** — 2026-07-25
- **Date:** 2026-07-25
- **Deciders:** Hrushikesh Gangisetty
- **Affects:** M6, M7, M8

## Context

The PRD specifies Kotlin, Jetpack Compose, MVVM + Repository, Coil for image loading — and then leaves two choices explicitly open: **"Ktor or Retrofit"** and **"Koin or Hilt"**. Both need deciding once, early, because every view model and every repository will depend on the answer.

What the app actually has to do shapes both choices:

- One authenticated user, a handful of screens.
- A multi-step upload pipeline with independent failure modes — capture, compress, upload, database write (M7).
- Offline draft persistence that survives process death and syncs on reconnect (M8.9, M8.10).
- Talk to Supabase, which has an official Kotlin SDK.

The offline requirement is the architecturally significant one. Drafts surviving process death means a local persistence layer that is a genuine second source of truth, not a cache — which makes the repository boundary load-bearing rather than ceremonial.

## Decision

**Accepted (specified by the PRD):**

- **Kotlin with Jetpack Compose** and Material 3, themed from the M1 design tokens ([ADR-0008](0008-design-tokens-single-source.md)).
- **MVVM + Repository**, with packages split `data` (remote, local, models) / `domain` / `ui` (screens, view models).
- **Coil** for image loading.
- **Repositories are the only thing that touches a data source.** View models depend on repositories, never on the Supabase client or the database directly — this is what makes the offline path in M8.9 possible without rewriting screens.
- **Kotlin models mirror the frozen schema contract** from M3, and a migration updates them in the same change (M6.6).

**Also accepted (decided 2026-07-25):**

| Choice | Decision | Reasoning |
|---|---|---|
| **DI framework** | **Hilt** | Compile-time verified, so a missing binding is a build error rather than a crash on a screen the owner opens once a week. Google-endorsed with the better Compose integration. Koin is lighter and simpler to learn; Hilt's annotation processing costs build time. For an app whose failure mode is "the shop owner cannot upload", compile-time safety is worth more than build speed. |
| **HTTP client** | **Ktor**, via the Supabase Kotlin SDK | The decision largely resolves itself: the official SDK is built on Ktor, so choosing Retrofit means running both. Ktor is Kotlin-first, coroutine-native, and shares idioms with the SDK. Retrofit is more widely known and better documented, which matters if unfamiliar. |
| **Distribution** | **Direct APK** | Single-owner internal tool. Avoids a developer account, store listing, privacy policy, and review delays. Requires a documented install and update path the owner can follow, and release signing configured in M6.1. |

**Testing target:** a modern device on the latest Android version. That
simplifies M7.3/M7.4, which only need the Android 13+ granular media
permission model rather than branching across legacy permission schemes —
though `minSdk` still needs setting deliberately in M6.1.

## Consequences

### What this makes easier

- A strict repository boundary means M8.9's offline drafts slot in behind the existing interface rather than forcing changes into screens.
- Hilt's compile-time checking catches wiring errors at build time — which matters when there is no QA pass and a broken screen may not be noticed for days.
- One HTTP stack means one set of timeout, retry, and error-handling conventions, and a smaller APK.

### What this makes harder

- Hilt's annotation processing slows builds, and its error messages are notoriously opaque when a binding graph breaks.
- Ktor is less familiar than Retrofit, with thinner Stack Overflow coverage. Expect slower going on the first non-trivial networking problem.
- MVVM + Repository is more structure than a handful of screens strictly needs. It pays off in M7 and M8; in M6 it will feel like overhead.

### What this commits us to

Once every view model resolves dependencies through the DI framework, switching is a mechanical change across every one of them. Cheap now, expensive at M8. Same for the HTTP client: a mixed stack is the worst outcome, so this should be settled before M6.5.

## Alternatives considered

| Alternative | Why not |
|---|---|
| Koin instead of Hilt | Lighter, faster builds, gentler learning curve — genuinely defensible. Loses compile-time verification, which is the property most worth having for an app with no QA pass. |
| Retrofit instead of Ktor | Better documented and more familiar — but the Supabase SDK already brings Ktor, so this means two HTTP stacks in one APK. |
| Manual DI, no framework | Viable at this size, and avoids Hilt's build cost. Degrades as the object graph grows, and every constructor change ripples by hand. |
| MVI instead of MVVM | Better for complex state, and M7's upload pipeline has real state complexity. The PRD specifies MVVM, and the deviation is not justified by the rest of the app. |
| XML views instead of Compose | The PRD specifies Compose, and offers no reason to revisit. |

## Open sub-questions

- **Hilt or Koin** — needed before M6.4.
- **Ktor or Retrofit** — needed before M6.5.
- **Minimum SDK version** — set in M6.1. Affects the media permission model in M7.3 and M7.4, since Android 13 changed it.
- **Room or DataStore for offline drafts** (M8.9). Drafts carry image references and sync state, which points at Room, but this can be decided at M8.
- **Open Question 5** — Play Store or sideloaded APK, which affects signing and release setup.

## References

- [prd.md](../../prd.md) — Tech Stack (Android), Android Requirements
- [ADR-0008](0008-design-tokens-single-source.md) — how the Compose theme derives from the design system
- [DEVELOPMENT_PLAN.md](../../DEVELOPMENT_PLAN.md) — M6, M7, M8
