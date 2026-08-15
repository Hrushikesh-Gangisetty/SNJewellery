package com.snjewellery.admin.domain.product

import com.snjewellery.admin.domain.RequestFailure

/** Where one photograph ended up, once it is in the bucket. */
sealed interface UploadImageResult {
    /**
     * [storagePath] is what `product_images.storage_path` stores, so the
     * website can derive renditions; [url] is what it stores in `url`, so
     * a consumer that only wants the picture need not derive anything.
     * ADR-0005 keeps both deliberately.
     */
    data class Uploaded(
        val storagePath: String,
        val url: String,
    ) : UploadImageResult

    data class Failed(val failure: RequestFailure) : UploadImageResult
}

/**
 * One uploaded photograph, ready to become a `product_images` row.
 *
 * [displayOrder] is zero-based and unique per product, and **position 0
 * is the primary image** — the one the website's product card shows and
 * the one its gallery opens on. It is the index in the list the owner
 * arranged in M7.5, which is what makes "the on-screen order is the
 * order that gets persisted" true rather than merely intended.
 */
data class UploadedImage(
    val storagePath: String,
    val url: String,
    val displayOrder: Int,
    /** 4:5 rather than the square default. See responsive.md §2. */
    val portrait: Boolean,
)

/** Whether the rows landed. */
sealed interface WriteImagesResult {
    data object Written : WriteImagesResult
    data class Failed(val failure: RequestFailure) : WriteImagesResult
}

/** Whether the objects are gone from the bucket. */
sealed interface RemoveImagesResult {
    data object Removed : RemoveImagesResult
    data class Failed(val failure: RequestFailure) : RemoveImagesResult
}

/** Where a product's photographs are, in Storage. */
sealed interface StoragePathsResult {
    data class Found(val paths: List<String>) : StoragePathsResult
    data class Failed(val failure: RequestFailure) : StoragePathsResult
}

/**
 * Puts a product's photographs in Storage.
 *
 * ── One photograph at a time, on purpose ─────────────────────────────
 * The obvious shape is "upload these five and tell me when you are
 * done". This one takes a single photograph, and the caller loops.
 *
 * That is what makes the two things M7 needs possible. Progress has to
 * be **per image** as well as overall, and a batch call can only report
 * a total. And M7.9 requires per-image retry after a failure part-way
 * through — which means the caller has to know exactly which ones
 * landed, and be able to ask again for only the rest.
 */
interface ProductImageRepository {

    /**
     * Uploads the staged photograph at [localUri] as a photograph of
     * [productId], and returns where it landed.
     *
     * [onProgress] is called as the bytes go out, from whichever thread
     * the HTTP client is writing on — it must be cheap and must not
     * assume it is on the main thread. `totalBytes` is what the request
     * declared, which for a local file is its size.
     *
     * **Does not throw** for an expected failure. A crash here would
     * strand a product row that has just been written.
     */
    suspend fun upload(
        productId: String,
        localUri: String,
        onProgress: (sentBytes: Long, totalBytes: Long) -> Unit,
    ): UploadImageResult

    /**
     * Makes [images] the complete set of `product_images` rows for
     * [productId] — replacing whatever is there, in one insert.
     *
     * One statement rather than one per photograph, because
     * `display_order` is unique per product: inserting them separately
     * would leave a half-ordered gallery behind if the third failed, and
     * a single insert either takes all the rows or none of them.
     *
     * Called once, after every upload has landed — a row pointing at an
     * object that is not there yet is a broken image on the website.
     *
     * **Replaces rather than appends**, so that calling it twice is the
     * same as calling it once. That matters because M7.9 lets the owner
     * retry a save that was interrupted, and the way this call gets made
     * twice is a response that was lost rather than a request that was
     * refused — where a plain insert would hit
     * `product_images_unique_order` and leave a retry that can never
     * succeed. It is also the semantic M8.3's edit screen needs: the set
     * on screen is the set that should exist.
     */
    suspend fun replaceImages(productId: String, images: List<UploadedImage>): WriteImagesResult

    /**
     * Deletes the objects at [storagePaths] from the bucket.
     *
     * This is the half of a rollback that the M3.3 cascade cannot do: a
     * `products` delete takes the image rows with it and leaves the
     * objects, which ADR-0005 names as a cost that accumulates silently.
     *
     * Called by M7.9 when an interrupted save is discarded, and by M8.4
     * when a product is deleted outright.
     *
     * Deleting nothing succeeds, and so does deleting an object that has
     * already gone — the caller wants the objects absent, not a report on
     * how absent they already were.
     */
    suspend fun remove(storagePaths: List<String>): RemoveImagesResult

    /**
     * Every object belonging to [productId].
     *
     * Read **before** the product row is deleted, because the M3.3 cascade
     * takes the `product_images` rows with it — and those rows are the only
     * record of where the objects are. Delete the product first and the
     * photographs become bytes nobody can name, paid for indefinitely.
     */
    suspend fun storagePathsFor(productId: String): StoragePathsResult
}
