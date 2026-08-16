# Deploying the database

Applying migrations to production, and what to do when one should not have been.

Produced by **M5.1**. The schema itself is [schema.md](../database/schema.md).

---

## 1 · Creating the production project

1. New Supabase project. Choose the region nearest the shop's customers —
   every image and every query crosses it.
2. Link the CLI to it, then push every migration:

   ```bash
   npx supabase link --project-ref <production-ref>
   npm run db:push
   ```

3. **Verify the policies against production, not against development.**

   ```bash
   npm run db:test-rls
   ```

   Thirty checks, and they are adversarial: they attempt to read a hidden
   category's products, to un-archive a product with the anonymous key, and to
   grant a role. A pass here is what makes the anonymous key safe to ship in the
   browser bundle.

4. Create the admin account. **There is no public sign-up** — Supabase
   dashboard → Authentication → Add user, then confirm the `users` row was
   created with role `admin` by the `on_auth_user_created` trigger.

5. Do **not** run the seed against production. `supabase/seed.sql` is sample
   jewellery; M5.6 enters the real catalogue.

---

## 2 · Applying a migration to production

Migrations are forward-only (CLAUDE.md §4) and go to production **before** the
code that depends on them. Deploying a build that queries a column production
lacks breaks the live site; deploying a column nothing reads yet breaks nothing.

```bash
npm run db:diff            # what production is missing
npm run db:push            # apply it
npm run db:test-rls        # policies still hold
npm run db:types           # regenerate the TypeScript contract
npm run db:check-contract  # the two clients still agree
```

The last two matter because a schema change is only complete when the migration,
`web/lib/data/database.types.ts` **and** the Kotlin models in
`data/models/SchemaContract.kt` all change together (CLAUDE.md §3.3). Nothing
fails a build to tell you otherwise, which is exactly why `db:check-contract`
exists.

---

## 3 · Reverting one

**There is no `down` migration, and that is deliberate.** A rehearsed forward
fix is safer than a reverse script written under pressure and never run.

1. **Do not edit the migration that is already applied.** Production has run it;
   editing the file makes the repository disagree with the database, silently.
2. Write a new migration that undoes what is wrong, and name it for what it
   does — `0009_drop_unused_column.sql`.
3. Apply it the same way as any other.

### When the data is the problem, not the schema

A migration that dropped or overwrote rows cannot be undone by another
migration. That is what point-in-time recovery is for — available on Supabase's
paid tiers, and **not on the free tier**, where the daily backup is the entire
safety net.

Worth knowing before, rather than after: if the catalogue is ever worth more
than the tier, the tier is the cheaper of the two.

---

## 4 · The Android app

The app talks to whichever project `android/local.properties` names. A build
handed to the shop owner must carry the **production** URL and anon key — a
release APK pointed at development shows an empty catalogue and uploads into a
database nobody is looking at. See [android.md](android.md).
