# Android build configuration

How the admin app is built, and the version and SDK-level decisions behind
it. **Why** the app is structured the way it is belongs in
[ADR-0007](../adr/0007-android-architecture.md); this documents the build.

Produced by M6.1.

---

## 1 · Toolchain

| Component | Version | Notes |
|---|---|---|
| Gradle | 8.11.1 | Wrapper committed — never build with a system Gradle |
| Android Gradle Plugin | 8.10.1 | |
| Kotlin | 2.0.21 | |
| Compose compiler | 2.0.21 | Ships with Kotlin from 2.0; not separately versioned |
| Compose BOM | 2025.04.00 | Pins every Compose artefact |
| JDK | 21 | Android Studio's bundled JBR is sufficient |

Every version lives in [`android/gradle/libs.versions.toml`](../../android/gradle/libs.versions.toml)
and nowhere else — a version written in a module's build file is one that
drifts from the other module's.

**These four move together.** AGP requires a minimum Gradle, Kotlin requires
a matching Compose compiler, and the Compose BOM is tested against a Kotlin
range. Changing one alone is the most common way this project's build breaks.
Check AGP's release notes for its required Gradle version and Compose's
compatibility map before touching any of them, and run
`./gradlew assembleDebug` immediately after.

Java and Kotlin both target **JVM 11**. Not 17 or 21: the target governs the
bytecode the Android toolchain consumes, and 11 is what current AGP expects
by default. The JDK running Gradle is a separate thing and is 21.

---

## 2 · SDK levels

| Level | Value | Android version |
|---|---:|---|
| `minSdk` | **26** | 8.0 Oreo (2017) |
| `targetSdk` | 35 | 15 |
| `compileSdk` | 35 | 15 |

`compileSdk` decides what the code may compile against and never affects
which devices can install. `targetSdk` declares which behaviour changes the
app has been tested against. Only `minSdk` excludes anyone.

### Why minSdk 26

The deciding fact is who uses this app: **three or four known administrators**
(Resolved Decision 4), on their own phones, distributing by **direct APK**
rather than through the Play Store (ADR-0007). There is no audience to reach
and no install-base statistic worth optimising. There is only the question of
whether every one of those people can run it — and if one cannot, that person
cannot upload a product at all, which is the app's entire purpose.

Android 8.0 is from 2017. Setting the floor there costs nothing in practice
while leaving no plausible working phone excluded.

**ADR-0007 pointed the other way and is worth answering directly.** It notes
that testing targets "a modern device on the latest Android version", which
"simplifies M7.3/M7.4, which only need the Android 13+ granular media
permission model rather than branching across legacy permission schemes". A
`minSdk` of 33 would indeed collapse that branch.

It is not needed, because the branch can be avoided a better way: the
**Photo Picker** (`ActivityResultContracts.PickVisualMedia`) requires *no
storage permission on any version* and is backported below 33. M7 therefore
gets one code path regardless of this setting, and the only runtime
permission left is `CAMERA`, which is unchanged across every supported
version. The simplification ADR-0007 wanted is achieved without excluding
devices. ADR-0007 explicitly left `minSdk` to this task rather than deciding
it, so this refines that note rather than contradicting a decision.

What 26 additionally buys:

- **Adaptive launcher icons only.** No legacy density-bucket PNGs, because
  every supported version understands `mipmap-anydpi-v26`.
- **`java.time` and full NIO** without desugaring.
- **Notification channels** unconditionally, if M9 ever notifies.

**Revisit if** an admin's phone turns out to predate Android 8 — raise nothing,
that phone is already excluded — or if a dependency demands a higher floor.
Raising `minSdk` is a one-line change in `app/build.gradle.kts` and is cheap
to reverse, unlike most decisions recorded in this repository.

---

## 3 · Secrets

The app reads its Supabase credentials from `BuildConfig`, populated by
Gradle from `android/local.properties`. That file is **gitignored** and holds
the SDK path, the Supabase URL and anon key, and the release signing
material. [`android/local.properties.example`](../../android/local.properties.example)
is the template.

Absent entries resolve to empty strings rather than failing the build, so a
fresh clone compiles before anyone has been handed credentials. **M6.5
validates them at startup**, which is where an empty URL has to become a
clear error rather than a confusing network failure. Until then, the app
makes no network call.

**Only the anon key ever goes near the APK.** The service-role key would be
extractable from the package in minutes and would grant full database access
with RLS bypassed — see [ADR-0004](../adr/0004-authentication-and-roles.md)
and CLAUDE.md §9. The app's write permission comes from the authenticated
admin's session, never from its key.

Verify after any change to the build configuration:

```bash
unzip -p app/build/outputs/apk/debug/app-debug.apk | grep -c service_role   # must be 0
```

---

## 4 · Release signing

Configured in `app/build.gradle.kts`, and **only when the keystore material
is present** in `local.properties` — so a debug build works on a machine that
has never seen the keystore.

Keep the keystore file itself outside the repository. Losing it means no
existing install can ever be updated: Android refuses an APK signed by a
different key, and the only recovery is an uninstall and reinstall on every
admin's phone.

The release build enables R8 with resource shrinking.
[`app/proguard-rules.pro`](../../android/app/proguard-rules.pro) is
deliberately empty; add a rule only with a comment naming the library that
needs it and the symptom without it.

---

## 5 · Building

```bash
cd android
./gradlew assembleDebug     # debug APK → app/build/outputs/apk/debug/
./gradlew assembleRelease   # needs signing material in local.properties
./gradlew lint              # Android Lint
```

`JAVA_HOME` must point at a JDK 21. Android Studio's bundled runtime works:

```bash
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"   # Git Bash on Windows
```

The debug build applies an `applicationId` suffix of `.debug`, so it installs
alongside a release build rather than replacing it — an admin keeps the
working app while a new build is being tested.
