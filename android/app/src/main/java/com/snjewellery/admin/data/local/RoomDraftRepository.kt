package com.snjewellery.admin.data.local

import com.snjewellery.admin.domain.RequestFailure
import com.snjewellery.admin.domain.draft.DraftRepository
import com.snjewellery.admin.domain.draft.PendingDraft
import com.snjewellery.admin.domain.product.ProductDraft
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Drafts in Room.
 *
 * ── A draft that will not parse is still a draft ─────────────────────
 * The JSON columns are decoded defensively: a tags array that cannot be
 * read becomes an empty list rather than an exception. The alternative is
 * a dashboard that crashes, or a piece the owner can never get rid of
 * because reading it throws. Losing the tags of one draft is a far
 * smaller loss than losing the piece.
 *
 * **The photographs are not treated that way.** A draft whose photo list
 * will not parse has no photographs, and uploading it would put a piece
 * in the catalogue with none — so it decodes to empty and M8.10's sync
 * treats an empty list as its own case rather than as a piece to send.
 */
@Singleton
class RoomDraftRepository @Inject constructor(
    private val dao: PendingDraftDao,
) : DraftRepository {

    override fun pending(): Flow<List<PendingDraft>> =
        dao.pending().map { rows -> rows.map { it.toDomain() } }

    override suspend fun byId(productId: String): PendingDraft? =
        dao.byId(productId)?.toDomain()

    override suspend fun save(draft: PendingDraft) = dao.upsert(draft.toEntity())

    override suspend fun delete(productId: String) = dao.delete(productId)

    private fun PendingDraftEntity.toDomain() = PendingDraft(
        productId = productId,
        draft = ProductDraft(
            name = name,
            categoryId = categoryId,
            purityId = purityId,
            weightGrams = weightGrams,
            description = description,
            tags = tags.decodeList(),
            featured = featured,
        ),
        photoUris = photoUris.decodeList(),
        savedAt = savedAt,
        // Null together: no attempt has failed since this was written.
        failure = failureOffline?.let { RequestFailure(offline = it, detail = failureDetail) },
    )

    private fun PendingDraft.toEntity() = PendingDraftEntity(
        productId = productId,
        name = draft.name,
        categoryId = draft.categoryId,
        purityId = draft.purityId,
        weightGrams = draft.weightGrams,
        description = draft.description,
        tags = Json.encodeToString(draft.tags),
        featured = draft.featured,
        photoUris = Json.encodeToString(photoUris),
        savedAt = savedAt,
        failureOffline = failure?.offline,
        failureDetail = failure?.detail,
    )

    private fun String.decodeList(): List<String> =
        runCatching { Json.decodeFromString<List<String>>(this) }.getOrDefault(emptyList())
}
