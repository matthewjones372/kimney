package io.github.matthewjones372.kimney

import kotlin.reflect.KClass
import kotlin.reflect.KProperty1

/**
 * The override chain for one transformation. Only ever read by the compiler plugin, which requires the whole chain,
 * from [into] to [transform], to be a single expression; none of these bodies runs with the plugin applied.
 */
public class Into<A, B> internal constructor() {
    public fun <T> withFieldConst(field: KProperty1<B, T>, value: T): Into<A, B> =
        throw KimneyNotApplied("withFieldConst")

    public fun <T> withFieldComputed(field: KProperty1<B, T>, compute: (A) -> T): Into<A, B> =
        throw KimneyNotApplied("withFieldComputed")

    public fun <S, T> withFieldRenamed(from: KProperty1<A, S>, to: KProperty1<B, T>): Into<A, B> =
        throw KimneyNotApplied("withFieldRenamed")

    /** Used for every pair below the root that [transformer] fits, before any other rule. */
    public fun <S, T> withTransformer(transformer: Transformer<S, T>): Into<A, B> =
        throw KimneyNotApplied("withTransformer")

    /** Used, in a partial call only, for every pair below the root that [transformer] fits. */
    public fun <S, T> withPartialTransformer(transformer: PartialTransformer<S, T>): Into<A, B> =
        throw KimneyNotApplied("withPartialTransformer")

    /**
     * Sends the entry [from] to [to] wherever an enum of [from]'s class becomes one of [to]'s, instead of to the
     * entry of the same name. Both must be written as the entries themselves: `Status.ARCHIVED`.
     */
    public fun <S : Enum<S>, T : Enum<T>> withEnumEntryRenamed(from: S, to: T): Into<A, B> =
        throw KimneyNotApplied("withEnumEntryRenamed")

    /**
     * Sends every entry with no entry of the same name, and no rename, to [to], wherever an enum becomes one of
     * [to]'s class. It is also what an entry compiled in after this call becomes.
     */
    public fun <T : Enum<T>> withEnumFallback(to: T): Into<A, B> = throw KimneyNotApplied("withEnumFallback")

    /**
     * Makes the sealed case [from] into the target case [to] wherever their sealed types meet, instead of into the
     * case of the same name, deriving it by every rule. Both are class literals: `Shape.Hexagon::class`.
     */
    public fun <S : Any, T : Any> withSealedCaseRenamed(from: KClass<S>, to: KClass<T>): Into<A, B> =
        throw KimneyNotApplied("withSealedCaseRenamed")

    /**
     * Makes every case with no case of the same name, no rename and no transformer into the object [to], wherever a
     * sealed type becomes [to]'s. It is also what a case compiled in after this call becomes.
     */
    public fun <T : Any> withSealedFallback(to: T): Into<A, B> = throw KimneyNotApplied("withSealedFallback")

    public fun transform(): B = throw KimneyNotApplied("transform")

    /** As [transform], collecting what does not fit into [Partial.Errors] rather than refusing to compile. */
    public fun transformPartial(): Partial<B> = throw KimneyNotApplied("transformPartial")
}

/** Starts an override chain. The source type is inferred: `user.into<_, UserDto>()`. */
public fun <A, B> A.into(): Into<A, B> = throw KimneyNotApplied("into")
