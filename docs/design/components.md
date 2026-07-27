# Component Inventory

Part of the [design system](README.md). Produced by M1.7.

Every component both platforms need, enumerated **before either is built**, so
M2.8 and M6 build against a known list rather than inventing as they go.

**States are not optional.** A component is not complete until every state in its
row exists. The default / hover / focus / active / disabled / loading / empty /
error columns are the actual work — the default state is usually the easy 20%.

Legend: **W** web only · **A** Android only · **B** both (shared concept, separate
implementation)

---

## Web

### Layout & navigation

| Component | Variants | States | Notes |
|---|---|---|---|
| `Header` | transparent-over-hero, solid | default, scrolled, mobile | Sticky. Logo or wordmark, nav, quick contact |
| `MobileDrawer` | — | closed, opening, open, closing | Focus trap, Esc closes, scroll lock, `aria-modal` |
| `Footer` | — | default | Shop details, hours, social, category links |
| `Container` | prose, content, wide, full | — | See [layout.md](layout.md) §5 |
| `Breadcrumb` | — | default | Home → Category → Product. Feeds M11.3 JSON-LD |
| `SkipLink` | — | hidden, focused | First focusable element. Jumps to `<main>` |

### Catalogue

| Component | Variants | States | Notes |
|---|---|---|---|
| `ProductCard` | default, featured | default, hover, focus, **sold**, loading (skeleton) | Image, name, category, short description. No purity or weight since 2026-07-27 |
| `ProductGrid` | — | default, loading, **empty** | 2/3/4 columns per [layout.md](layout.md) §5 |
| `CategoryChip` | default, active | default, hover, focus, disabled | Also the filter control |
| `SoldBadge` | — | default | The word "Sold" — never colour alone |
| `Pagination` | numbered, load-more | default, loading, first-page, last-page | Keyset cursors (M10.6) |

### Product detail

| Component | Variants | States | Notes |
|---|---|---|---|
| `ImageGallery` | single-image, multi-image | default, loading, image-error, **no-images** | Keyboard arrows, thumbnail strip, fixed aspect ratio |
| `GalleryThumbnail` | — | default, active, hover, focus | |
| `SpecList` | — | default, missing-optional, **empty (hides)** | Colours. Uses `text-spec`. Purity and weight were removed on 2026-07-27 — see `MetalRates` |
| `RelatedProducts` | — | default, loading, empty (hides) | |

### Conversion — the site's entire purpose

| Component | Variants | States | Notes |
|---|---|---|---|
| `WhatsAppButton` | primary, inline, floating | default, hover, focus, active | Pre-filled message with product name + URL |
| `CallButton` | primary, inline | default, hover, focus, active | `tel:` |
| `DirectionsButton` | primary, inline | default, hover, focus, active, **unavailable** | Hides until Maps location supplied |
| `ContactBar` | — | default | Header/footer quick contact |

### Rates

| Component | Variants | States | Notes |
|---|---|---|---|
| `MetalRates` | — | default, **unpublished (hides)** | Today's gold and silver rate per gram, with the timestamp the owner last set it |

`MetalRates` replaced per-piece purity and weight on 2026-07-27. It renders
**nothing** until both metals have a real rate: half a panel, or a placeholder, is
worse than no panel because a customer would act on it.

It is a quiet panel in the page flow, never a strip pinned under the header —
[brand.md](brand.md) §6 names banner stacking as an anti-pattern. The timestamp is
formatted in `Asia/Kolkata` explicitly: the shop is in Markapur, so "9:12 AM" must
mean the shop's morning and not the reader's.

All four are **minimum 44 × 44 px** touch targets ([layout.md](layout.md) §6).
They are pressed one-handed, often on the move.

### Search & filter (M10)

| Component | Variants | States | Notes |
|---|---|---|---|
| `SearchInput` | header, page | default, focus, typing, loading, **has-value** | Debounced. Clear button |
| `FilterPanel` | sidebar (desktop), sheet (mobile) | default, open, applied | Composable filters |
| `FilterChip` | — | default, active, hover, focus | Removable |
| `ActiveFilters` | — | default, empty (hides) | Chips + clear-all |
| `SortControl` | — | default, open | Latest, featured |

### Primitives

| Component | Variants | States |
|---|---|---|
| `Button` | primary, secondary, ghost, destructive | default, hover, focus, active, disabled, loading |
| `Link` | inline, standalone, nav | default, hover, focus, visited |
| `Input` | text, search | default, focus, filled, error, disabled |
| `Select` | — | default, open, focus, disabled |
| `Skeleton` | text, image, card | shimmer, static (reduced-motion) |
| `EmptyState` | — | default | Icon, message, suggested action |
| `ErrorState` | inline, page | default, retrying |
| `Badge` | neutral, accent | default |
| `Heading` | display-xl … heading-s | — |
| `AspectBox` | product, product-portrait, hero, hero-mobile | — | Reserves space so images cannot shift layout |

---

## Android

The admin app is a **tool**, not a storefront. Prefer Material 3 defaults themed
with the tokens over bespoke components — every custom control is maintenance for
an app with one user type.

### Structure

| Component | Variants | States | Notes |
|---|---|---|---|
| `TopAppBar` | default, with-actions, scrolled | default, scrolled | |
| `BottomNav` | — | default | Dashboard, Products, Categories |
| `AuthScreen` | — | idle, validating, submitting, **wrong-credentials**, **no-network**, **not-admin** | Three distinct failures, three distinct messages (M6.7, M6.9) |
| `DashboardTile` | — | default, loading, error | Total, new, featured, recent |

### Product management

| Component | Variants | States | Notes |
|---|---|---|---|
| `ProductForm` | create, edit | pristine, dirty, validating, invalid, submitting, **offline-draft** | Every PRD field |
| `FormField` | text, number, multiline, tags | default, focus, filled, error, disabled | |
| `CategoryPicker` | — | default, open, loading, empty | From live categories |
| `PurityPicker` | — | default, open | From `purities` table — extensible |
| `WeightField` | — | default, focus, invalid | Grams, decimal |
| `FeaturedToggle` | — | on, off, pending, **failed (rolled back)** | Optimistic with rollback |
| `ImagePickerTile` | camera, gallery, selected, add | empty, selected, **primary**, uploading, failed, retryable | Reorderable |
| `UploadProgress` | per-image, overall | idle, uploading, paused, failed, complete | |
| `ProductListRow` | — | default, pressed, sold, archived, **draft-pending** | Thumbnail, name, category, badges |
| `StatusBadge` | featured, sold, archived, draft | default | |
| `ConfirmDialog` | destructive, neutral | default, processing | Delete needs it |
| `Snackbar` | info, success, error, **retry** | default | Failed uploads must stay retryable |
| `SyncIndicator` | — | synced, pending, syncing, **failed** | Offline drafts (M8.10) |

### Shared patterns

| Component | States |
|---|---|
| `LoadingState` | skeleton, spinner |
| `EmptyState` | default |
| `ErrorState` | default, retrying |
| `PullToRefresh` | idle, refreshing |

---

## Rules

1. **Every state in the table exists before the component is done.** The empty,
   error, and loading states are the deliverable, not an afterthought.
2. **No component uses a raw visual value.** Everything from
   [tokens.json](tokens.json). M2 and M6 acceptance criteria test this.
3. **Prefer shadcn/ui and Material 3 defaults, themed.** A bespoke control needs a
   reason.
4. **Every interactive component is keyboard-operable and has a visible focus
   state.** No exceptions — verified in M12.6.
5. **State is never communicated by colour alone** — see
   [accessibility.md](accessibility.md).
6. **If a component is needed that is not on this list, add it here first.** The
   inventory is the contract; an undocumented component is a gap in the design
   system, not a shortcut.

## Coverage check

Walking the PRD's page and screen lists against this inventory:

| PRD surface | Covered by |
|---|---|
| Home | Header, Footer, ProductGrid, ProductCard, CategoryChip, ContactBar, MetalRates |
| Catalogue | ProductGrid, ProductCard, Pagination, FilterPanel, CategoryChip |
| Product Details | ImageGallery, GalleryThumbnail, SpecList, RelatedProducts, WhatsApp/Call/Directions |
| Search | SearchInput, FilterPanel, FilterChip, ActiveFilters, EmptyState |
| Contact | Footer, ContactBar, CallButton, WhatsAppButton, DirectionsButton |
| About | Container (prose), Heading |
| App: Auth | AuthScreen |
| App: Dashboard | TopAppBar, BottomNav, DashboardTile |
| App: Add Product | ProductForm, FormField, CategoryPicker, PurityPicker, WeightField, ImagePickerTile, UploadProgress |
| App: Product Management | ProductListRow, StatusBadge, FeaturedToggle, ConfirmDialog, Snackbar |
| App: Category Management | CategoryPicker, ConfirmDialog, ProductListRow |
| App: Search | SearchInput, FormField |

No PRD surface lacks a component.
