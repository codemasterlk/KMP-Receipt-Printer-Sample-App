package dev.escpos.sample.domain.repository

import dev.escpos.sample.domain.model.SavedSetup

/**
 * Where the form's [SavedSetup] lives between launches — an interface so the domain and
 * presentation layers never know whether it's `SharedPreferences`, the Windows registry, or
 * `NSUserDefaults` underneath (see `dev.escpos.sample.data.repository.SetupRepositoryImpl`).
 */
interface SetupRepository {
    /** What was saved last, or a [SavedSetup] with every field `null` if nothing ever was (or a
     *  stored value no longer parses). Synchronous and cheap — read once at startup, before the
     *  first frame, so the form never flashes its defaults. */
    fun load(): SavedSetup

    /** Replaces the saved setup. A `null` field removes its stored value. */
    suspend fun save(setup: SavedSetup)
}
