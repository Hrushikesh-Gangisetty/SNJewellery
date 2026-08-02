package com.snjewellery.admin.domain.product

/**
 * What is wrong with a field, before anything is sent.
 *
 * Each case mirrors a constraint the database would enforce anyway. That
 * is the point: the database is the authority, and these exist to say so
 * *before* a round trip, in words the owner can act on — not instead of
 * it. A rule invented here that the schema does not hold would reject a
 * piece the catalogue would have accepted.
 */
enum class FieldProblem {
    /** `products_name_not_blank`. */
    NameBlank,

    /** `category_id` is `not null`, and no default exists. */
    CategoryMissing,

    /**
     * Not a number at all. Before M7.2 this was **silently discarded**:
     * `toDoubleOrNull()` returned null and the piece saved as "not
     * weighed", so a mistyped weight vanished without a word. Found
     * while driving the form in M7.1.
     */
    WeightNotANumber,

    /** `products_weight_positive`: null, or greater than zero. */
    WeightNotPositive,
}

/**
 * Shape checks for the Add Product form.
 *
 * No Android imports — `domain` stays testable on the JVM, the same rule
 * [CredentialRules][com.snjewellery.admin.domain.auth.CredentialRules]
 * follows.
 */
object ProductFormRules {

    fun validateName(name: String): FieldProblem? =
        if (name.isBlank()) FieldProblem.NameBlank else null

    fun validateCategory(categoryId: String?): FieldProblem? =
        if (categoryId == null) FieldProblem.CategoryMissing else null

    /**
     * Weight is **optional**, so blank is valid — a silver anklet may
     * never be weighed. Anything else must be a positive number, because
     * that is exactly what the column accepts.
     */
    fun validateWeight(weight: String): FieldProblem? {
        val trimmed = weight.trim()
        if (trimmed.isEmpty()) return null

        val value = trimmed.toDoubleOrNull() ?: return FieldProblem.WeightNotANumber
        // Also catches NaN, which is not > 0 — a value `toDoubleOrNull`
        // will happily return for "NaN".
        return if (value > 0.0) null else FieldProblem.WeightNotPositive
    }
}
