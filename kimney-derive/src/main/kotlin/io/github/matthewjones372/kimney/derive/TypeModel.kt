package io.github.matthewjones372.kimney.derive

/** How the engine sees types. The FIR and IR adapters each implement it, so both run the same rules. */
interface TypeModel<T> {
    fun render(type: T): String

    fun isSubtypeOf(sub: T, sup: T): Boolean

    fun construction(type: T): Construction<T>

    /** The type of a readable property of [owner] with exactly this name, if it has one. */
    fun property(owner: T, name: String): T?

    /** The entry names of an enum class, or null if [type] is not one. */
    fun enumEntries(type: T): List<String>?

    /** The direct subclasses of a non-generic sealed class or interface, or null if [type] is not one. */
    fun sealedCases(type: T): List<Case<T>>?

    fun isObject(type: T): Boolean

    fun isNullable(type: T): Boolean

    fun nonNull(type: T): T

    /** The one property a value class holds, or null if [type] is not a non-generic value class. */
    fun valueClass(type: T): Param<T>?
}

/** One direct subclass of a sealed type, matched to the other side by its simple [name]. */
data class Case<T>(val name: String, val type: T)

sealed interface Construction<out T> {
    data class Primary<T>(val params: List<Param<T>>) : Construction<T>

    data class NotPublic(val visibility: String) : Construction<Nothing>

    data object SecondaryOnly : Construction<Nothing>

    /** An interface, object, enum, abstract class, nullable type or standard-library type: nothing to construct. */
    data object NotAClass : Construction<Nothing>
}

data class Param<T>(val name: String, val type: T, val hasDefault: Boolean)
