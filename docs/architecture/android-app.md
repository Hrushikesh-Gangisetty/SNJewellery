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
│   └── config/            No Android imports. No Compose. No Supabase.
│
├── data/                  Repository IMPLEMENTATIONS and their sources.
│   ├── config/            BuildConfig-backed configuration
│   ├── remote/            Supabase and network            (M6.5)
│   ├── local/             Room and DataStore, offline drafts (M8)
│   └── models/            Mirrors of the schema contract   (M6.6)
│
├── di/                    Hilt modules. Bindings only, no logic.
│
└── ui/
    ├── theme/             Tokens → Material 3 theme (M6.2)
    ├── components/        Shared composables            (M6.7 onward)
    └── screens/           One package per screen:
        └── shell/           the composable and its view model together
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

If step 2 needs something constructed rather than injected — a Supabase
client, a Room database — give it its own `@Provides` module rather than
adding to `DataModule`, which stays bindings-only.
