package io.github.matthewjones372.kimney

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

    public fun transform(): B = throw KimneyNotApplied("transform")

    /** As [transform], collecting what does not fit into [Partial.Errors] rather than refusing to compile. */
    public fun transformPartial(): Partial<B> = throw KimneyNotApplied("transformPartial")
}

/** Starts an override chain. The source type is inferred: `user.into<_, UserDto>()`. */
public fun <A, B> A.into(): Into<A, B> = throw KimneyNotApplied("into")
