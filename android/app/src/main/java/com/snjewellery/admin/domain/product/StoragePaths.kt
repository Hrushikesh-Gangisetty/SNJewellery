package com.snjewellery.admin.domain.product

/**
 * Where a photograph lives in Storage.
 *
 * [ADR-0005](docs/adr/0005-image-storage-and-renditions.md) §5: this is
 * **the only way an image location is constructed**, in either client. A
 * path assembled by hand somewhere else is a path that drifts, and every
 * rendition the website asks for is derived from this string — so a
 * second spelling of it would not fail loudly, it would serve a broken
 * image on a product page nobody looks at twice.
 *
 * No Android imports, so `domain` stays testable on the JVM.
 */
object StoragePaths {

    /** Created by `supabase/migrations/20260726000400_storage.sql`. */
    const val PRODUCT_IMAGES_BUCKET = "product-images"

    /**
     * `products/{product_id}/{image_id}.webp`, exactly as ADR-0005 has
     * it.
     *
     * The extension is fixed rather than taken from the file, because
     * M7.6 makes every uploaded photograph a WebP. If that ever stops
     * being true this is the line that has to change, and the bucket's
     * `allowed_mime_types` is the second.
     */
    fun productImage(productId: String, imageId: String): String =
        "products/$productId/$imageId.$IMAGE_EXTENSION"

    private const val IMAGE_EXTENSION = "webp"
}
