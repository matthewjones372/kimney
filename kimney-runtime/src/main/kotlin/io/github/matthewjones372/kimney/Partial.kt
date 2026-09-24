package io.github.matthewjones372.kimney

/** The result of a transformation that may not fit: the value, or every reason it could not be built. */
public sealed interface Partial<out T> {
    public data class Ok<T>(val value: T) : Partial<T>

    /** Never empty: an `Errors` with nothing in it would be an `Ok`. */
    public data class Errors(val errors: List<PartialError>) : Partial<Nothing>
}

/** One reason a partial transformation failed: [path] as the compile-time errors name it, and what went wrong. */
public data class PartialError(val path: String, val message: String)

/** The value, or null for `Errors`. */
public fun <T> Partial<T>.valueOrNull(): T? = (this as? Partial.Ok)?.value

/** The plugin replaces every call with the derived construction, collecting errors instead of refusing to compile. */
public fun <B> Any?.transformIntoPartial(): Partial<B> = throw KimneyNotApplied("transformIntoPartial")

/**
 * These errors, as seen from where the value they belong to sits. An empty path becomes [path]; a path written from
 * its own root, as kimney writes them (`Booking.checkIn`), has that root replaced by [path].
 */
public fun Partial.Errors.relocatedTo(path: String): List<PartialError> = errors.map { error ->
    val below = error.path.substringAfter('.', missingDelimiterValue = "")
    error.copy(path = if (below.isEmpty()) path else "$path.$below")
}

/** How one type becomes another when it may not: pass it to [Into.withPartialTransformer] for a partial call. */
public fun interface PartialTransformer<in A, out B> {
    public fun transform(source: A): Partial<B>
}
