# ADR-0004: Row-level security as the security boundary

- **Status:** ✅ Accepted
- **Date:** 2026-07-25
- **Deciders:** Hrushikesh Gangisetty
- **Affects:** M3.7, M3.8, M6.7–M6.9, and every data access in the project

## Context

With no application server between the clients and the database ([ADR-0003](0003-supabase-backend.md)), access control has to live somewhere unavoidable. There are two candidates: the clients, or the database.

Putting it in the clients means the public website's key is trusted to only make permitted queries. That trust is misplaced — anything shipped to a browser is inspectable and modifiable, so an anonymous key with write permission is a write endpoint for anyone who opens developer tools.

The access requirements themselves are simple, which is what makes a strict boundary affordable:

- **Customers** are anonymous, never authenticate, and only read published products.
- **The shop owner** authenticates with email and password, and may write everything.
- **Nobody else** may write anything.

The PRD requires secure authentication, role-based access, storage access policies, and RLS explicitly. It also says only authorised users may access the Android app.

## Decision

**Row-level security is the security boundary.** Not client code, not a check in a component, not an assumption about which queries the website will make.

Concretely:

1. **The website uses the anonymous key and has no write path at all.** Its policies permit `SELECT` only, and only on products that are not archived whose category is visible.
2. **The Android app authenticates via Supabase Auth** with email and password. Public sign-up is disabled; accounts are created deliberately (M3.8).
3. **Admin writes are gated on `users.role = 'admin'`** in the policy, and the app additionally checks the role after login (M6.9) so an unauthorised user gets an explanation rather than a wall of failed requests.
4. **`role` is not self-assignable.** A user may read their own row and cannot escalate.
5. **Storage mirrors this**: public read of the product bucket, admin-only write.
6. **The service-role key is never shipped to any client** — not the website, not the app. M5.3 verifies this by searching the built output.

The rule that follows, and which [docs/architecture/](../architecture/) states as an invariant: **if a feature appears to require the service-role key from a client, the policy is wrong.** Fix the policy.

## Consequences

### What this makes easier

- A bug in a client cannot leak or corrupt what the policy forbids. The blast radius of a front-end mistake is bounded by the database.
- Hiding a category or archiving a product is enforced once, in one place, for both clients and every future client.
- The website needs no session handling, no auth UI, and no protected routes — it has nothing to protect.

### What this makes harder

- **Policies must be tested adversarially.** A policy that permits the happy path may still leak through an unexpected join. M3.7's acceptance criteria therefore require *attempting* the attacks — reading a hidden category's products via `product_images`, escalating a role — not merely confirming that legitimate reads work.
- Every new table needs its policies designed at creation. A table with RLS enabled and no policy denies everything; a table without RLS enabled exposes everything. The second failure is silent, which makes it the dangerous one.
- Debugging an unexpectedly empty result set means checking policies as well as query logic.

### What this commits us to

Every future feature must be expressible as an RLS policy. If M13's AI features need server-side processing with elevated access, that runs in an Edge Function with the key held server-side — never in a client.

## Alternatives considered

| Alternative | Why not |
|---|---|
| Application-level checks in client code | Client code is inspectable and modifiable. This is not a security boundary. |
| A backend API in front of the database | A real option, and how this would be done at larger scale — but it means building and operating a server for a catalogue with one writer. RLS gets the same guarantee with less to maintain. |
| Service-role key in the Android app, since only the owner has it | The key would sit in a distributed APK, extractable in minutes, granting full database access with RLS bypassed. Categorically unacceptable. |
| Anonymous writes with validation triggers | Turns the public key into a write endpoint and moves authorisation into trigger logic — strictly worse than a policy. |

## Open sub-questions

- **Open Question 4** — single admin or multiple users with narrower permissions. The policy model is much cheaper to design for multiple roles now than to retrofit. Needs an answer before M3.7 is finalised.
- **Open Question 5** — Android distribution. Sideloaded APKs have no store-level integrity guarantee, which raises the value of the session and role checks being correct.
- Password reset flow for the admin account: not yet specified. Needed before M5 if the owner is the only account holder.

## References

- [prd.md](../../prd.md) — Security; Android Admin Application → Authentication
- [ADR-0003](0003-supabase-backend.md) — why there is no application server
- [docs/database/](../database/) — the policy model and its adversarial checks
