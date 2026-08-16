package com.snjewellery.admin.data.local

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

/**
 * A draft as it sits on the device.
 *
 * ── Why this is not the domain type ──────────────────────────────────
 * The same reason `data/models/SchemaContract.kt` is not: this is a row
 * shape, and ADR-0007 keeps repositories returning domain models so a
 * column rename never reaches a screen. `PendingDraft` is what the app
 * thinks in.
 *
 * ── Lists are stored as JSON, not as related tables ──────────────────
 * [tags] and [photoUris] are JSON arrays in a text column. A second table
 * with a foreign key is what a database course would say, and it is the
 * wrong shape here: nothing ever queries a draft *by* a tag or a
 * photograph, the lists are read and written whole, and ordering the
 * photographs would need a position column that the JSON array gives for
 * free. It also means no `TypeConverter` registry to keep in step.
 *
 * [failureOffline] and [failureDetail] are null together — there has been
 * no attempt, or none that failed.
 */
@Entity(tableName = "pending_drafts")
data class PendingDraftEntity(
    /**
     * The id the save attempt chose, not a key of this table's own. See
     * `PendingDraft` for why that matters.
     */
    @PrimaryKey val productId: String,
    val name: String,
    val categoryId: String,
    val purityId: String?,
    val weightGrams: Double?,
    val description: String?,
    /** JSON array. */
    val tags: String,
    val featured: Boolean,
    /** JSON array, in the owner's order. The first is the main image. */
    val photoUris: String,
    val savedAt: Long,
    val failureOffline: Boolean?,
    val failureDetail: String?,
)

@Dao
interface PendingDraftDao {
    /**
     * Oldest first: the piece that has been waiting longest is the one
     * the owner is most likely to have given up on, so it is the one to
     * show and to send first.
     */
    @Query("SELECT * FROM pending_drafts ORDER BY savedAt ASC")
    fun pending(): Flow<List<PendingDraftEntity>>

    @Query("SELECT * FROM pending_drafts WHERE productId = :productId")
    suspend fun byId(productId: String): PendingDraftEntity?

    /**
     * REPLACE, so re-writing a draft after a second failed attempt
     * updates it rather than failing on the primary key. A piece has one
     * draft, and the newest is the true one.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(draft: PendingDraftEntity)

    @Query("DELETE FROM pending_drafts WHERE productId = :productId")
    suspend fun delete(productId: String)
}

/**
 * The device's own database. One table today.
 *
 * `exportSchema` is on and the generated JSON is committed — see the
 * `room.schemaLocation` note in build.gradle.kts. A migration written
 * against a schema nobody kept is written against a guess.
 */
@Database(entities = [PendingDraftEntity::class], version = 1, exportSchema = true)
abstract class AdminDatabase : RoomDatabase() {
    abstract fun pendingDrafts(): PendingDraftDao
}
