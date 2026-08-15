# Android app structure

The admin app's package layout and the rules that hold it together.
**Why** these choices were made is [ADR-0007](../adr/0007-android-architecture.md);
this documents the resulting structure. Build configuration is
[android-build.md](android-build.md).

Produced by M6.3 and M6.4.

---

## 1 · Packages

```
com.snjewellery.admin/
├── SnJewelleryApp.kt      @HiltAndroidApp — the dependency graph's root
├── MainActivity.kt        @AndroidEntryPoint — the single activity
│
├── domain/                Business types and repository INTERFACES.
│   ├── auth/              Sign-in, session, role     (M6.7–M6.9)
│   ├── dashboard/         The four PRD metrics           (M6.11)
│   └── config/            No Android imports. No Compose. No Supabase.
│
├── data/                  Repository IMPLEMENTATIONS and their sources.
│   ├── auth/              Supabase Auth, encrypted session (M6.7–M6.9)
│   ├── config/            BuildConfig-backed configuration
│   ├── dashboard/         Catalogue counts               (M6.11)
│   ├── remote/            Supabase and network            (M6.5)
│   ├── local/             Room and DataStore, offline drafts (M8)
│   └── models/            Mirrors of the schema contract   (M6.6)
│
├── di/                    Hilt modules. Bindings only, no logic.
│
└── ui/
    ├── theme/             Tokens → Material 3 theme (M6.2)
    ├── navigation/        The authenticated graph   (M6.10)
    ├── components/        Shared composables            (M6.7 onward)
    └── screens/           One package per screen:
        ├── root/            which world the app is in
        ├── login/           sign-in                     (M6.7)
        ├── access/          refused / could not check   (M6.9)
        └── dashboard/       start destination     (M6.10, M6.11)
```

Packages appear as the milestone that needs them arrives. An empty
package is not structure.

---

## 2 · The rules

These are what make the structure worth having. Each is enforced by
something, not just asserted.

### 2.1 Dependencies point inward

`ui` → `domain` ← `data`

A view model depends on a repository **interface** in `domain`. It never
names the implementation. `domain` depends on neither of the others and
imports nothing from Android — which is what keeps it testable on the
JVM without a device.

*Check:* `ShellViewModel`'s generated Dagger factory takes
`ConfigRepository`, the interface. If a view model ever named an
implementation, that factory would say so.

### 2.2 Repositories are the only thing that touches a data source

ADR-0007's central rule. `BuildConfigRepository` is the **only** file in
the app that mentions `BuildConfig`; when M6.5 lands, the Supabase client
will be reachable only from `data/remote`.

This is what makes the offline path in M8.9 possible without rewriting
screens: a repository can start answering from Room instead of the
network, and nothing above it changes.

*Check:* `grep -rn "BuildConfig\." app/src/main/java` returns files in
`data/` only.

### 2.3 Every binding is verified when the app compiles

The reason ADR-0007 chose Hilt over Koin. A repository nobody wired up
fails the build rather than crashing on a screen the owner opens once a
week.

*Check:* deleting the `@Binds` in `di/DataModule.kt` produces
`[Dagger/MissingBinding] ConfigRepository cannot be provided` at compile
time. Verified in M6.4, not assumed.

### 2.4 Screens split stateful from stateless

Every screen is two composables:

```kotlin
@Composable fun ShellScreen(viewModel: ShellViewModel = hiltViewModel())  // resolves
@Composable internal fun ShellScreen(uiState: ShellUiState)               // renders
```

The stateful one resolves the view model and does nothing else. The
stateless one takes plain values, and is what `@Preview` and tests
render.

Without the split a preview needs a Hilt graph, which is how Compose
previews quietly stop working in an app of any size.

### 2.5 View models expose `StateFlow`, not Compose state

View models in this app do not depend on Compose. They stay testable
without a UI toolkit, and a screen can be rewritten without touching
one. Screens collect with `collectAsStateWithLifecycle`, so a screen
that is off-screen stops collecting.

### 2.6 UI state is a type, not scattered booleans

`ShellUiState` is one object describing everything the screen renders.
Where a distinction leads somewhere different — configured versus not —
it is a `sealed interface` in `domain`, so a `when` over it is
exhaustive and a new case is a compile error at every call site.

### 2.6a Session state and navigation are separate concerns

**Session state decides which world you are in; the graph decides where
you are within it.** `RootViewModel` maps the persisted session and the
M6.9 role check onto `SessionState`, and `MainActivity` chooses between
the login screen, the blocked screen, and the authenticated `NavHost`.
Login is **not** a navigation destination.

Making it one is the obvious shortcut and it is wrong twice over: two
things would own the same answer, and the back stack would cheerfully
return a signed-out user to a screen they may no longer see. Keeping the
split is also what makes logout free — it clears the session and the
graph is replaced wholesale, with no navigation state left to get out of
step.

Navigation Compose with **type-safe routes** (`@Serializable` objects in
`ui/navigation/Destinations.kt`), so a misspelled destination is a
compile error rather than a runtime crash — the same argument ADR-0007
made for Hilt. One destination exists today; M7 and M8 add theirs.

### 2.6b The public policy does not apply to this app

The website reads with the anonymous key, whose policy hides archived
products and hidden categories for it. **The admin app authenticates,
and `products_admin_all` returns every row** — so any filtering an admin
screen wants is that screen's own job.

The dashboard therefore writes `archived = false` on every query. Get it
wrong and the owner is told they have more pieces than a customer can
see, with nothing anywhere to indicate why. Expect to repeat this filter
in M8's catalogue list; it is not a default and cannot be.

*Check:* against the seeded dev project, the anonymous key counts 11
non-archived products and an admin counts 12 — the difference is the one
product in a hidden category, which the owner must be able to manage.

### 2.6c A write the customer can see comes last

A save that spans several requests orders them so that **the row a
customer can reach is written after everything it depends on**. For a
product that means: photographs into Storage first, then the `products`
row, then the `product_images` rows.

The reason is not tidiness. The obvious order — row first, so the images
have a parent to attach to — is fine until the connection dies half-way,
and then there is a piece in the catalogue with two of its five
photographs and the only way to undo it is a `DELETE` over the same
connection that just failed. **A compensating write cannot be relied on
to run at the moment it is most needed.** Ordered the other way, an
interruption leaves objects in a bucket that nothing points at and no row
at all, so nothing public is ever half-made — and that holds without the
network's cooperation.

Two consequences to carry into M8:

- **The client chooses the id.** A photograph's path is
  `products/{product_id}/…` ([ADR-0005](../adr/0005-image-storage-and-renditions.md) §2),
  and it has to be known before there is a row to read it from. So
  `ProductRepository.create` takes the id rather than letting the column
  default.
- **Every step is safe to repeat.** Resuming an interrupted save re-runs
  whatever did not visibly finish, and the request that most often needs
  repeating is one whose *response* was lost rather than one that was
  refused. So the product insert answers with the existing row when its
  id is already present, and `replaceImages` clears before it inserts. A
  retry that can hit a unique violation is a retry that can never
  succeed.

- **What reached the server is recorded where the process cannot take it.**
  The record of a part-finished attempt lives on the screen's
  `SavedStateHandle`, written through on every change rather than at a
  checkpoint — there is no moment at which Android tells an app it is
  about to be reclaimed, and being reclaimed is likeliest precisely
  mid-upload, holding several megabytes of bitmap. The rule is that **no
  object exists in the bucket that this record does not mention**, so a
  reopened screen can always offer both ways out instead of coming back
  blank over storage nobody remembers.

*Check:* with the network dropped mid-upload, `select count(*) from
products` is unchanged, and the objects under `products/{id}/` are
removed by Discard. Force-stopped mid-upload, the screen reopens saying
what got as far as the bucket.

### 2.6d A write that changed nothing is not a write that worked

**PostgREST answers `204 No Content` for an `UPDATE` or `DELETE` that
matched zero rows, exactly as it does for one that matched.** Verified
against the live project: an anonymous `PATCH` on a product returns 204
and changes nothing, and `Prefer: return=representation` is what reveals
the empty result.

So `try { … } catch` around a write is **not** enough to know it
happened. Any write whose success the UI acts on asks for the changed
rows back — `select()` on the update — and treats an empty result as its
own outcome, distinct from both success and a transport failure.

It matters most where the UI is optimistic. M8.5's toggles show the new
value before the server has agreed, and roll back if it refuses; a 204
over zero rows would be read as agreement, leaving the owner looking at
a state the catalogue does not have and no way to find out.

The distinction is not the same for every verb. For a **delete**, zero
rows means the row is already absent, which is the state the caller
wanted — so it succeeds. For an **update**, zero rows means the change
did not happen.

*Check:* toggling a status on a piece deleted from another device shows
"no longer in the catalogue" with a refresh, not a switch that stays
flipped.

### 2.7 No screen declares a visual value

Colour, size, radius, duration: all from `MaterialTheme` or `Tokens`,
never a literal. See [ADR-0008](../adr/0008-design-tokens-single-source.md)
and M6.2.

---

## 3 · Adding a feature

1. Model it in `domain/` — the types, and the repository interface.
2. Implement the repository in `data/`, talking to exactly one source.
3. Bind it in `di/DataModule.kt`.
4. Add `ui/screens/<name>/` with the view model and both composables.
5. If it is a new destination, declare it in `ui/navigation/Destinations.kt`
   and wire it into `AdminNavHost`.

If step 2 needs something constructed rather than injected — a Supabase
client, a Room database — give it its own `@Provides` module rather than
adding to `DataModule`, which stays bindings-only.
