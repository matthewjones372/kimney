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
