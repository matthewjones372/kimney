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

    /** The simple name of a direct subclass of a sealed type, which is what a case is matched by; else null. */
    fun caseName(type: T): String?

    /** A read-only collection, map or reference array, with its element type; null for anything else. */
    fun container(type: T): Container<T>?
}

/** For a [Kind.MAP], [element] is the value type and [key] the key type. */
data class Container<T>(val kind: Kind, val element: T, val key: T? = null) {
    /** A mutable kind is its [readOnly] kind to every rule; only the target's interface differs. */
    enum class Kind(val display: String, readOnly: Kind? = null) {
        LIST("a List"),
        SET("a Set"),
        COLLECTION("a Collection"),
        ITERABLE("an Iterable"),
        MAP("a Map"),
        ARRAY("an Array"),
        MUTABLE_LIST("a MutableList", LIST),
        MUTABLE_SET("a MutableSet", SET),
        MUTABLE_COLLECTION("a MutableCollection", COLLECTION),
        MUTABLE_ITERABLE("a MutableIterable", ITERABLE),
        MUTABLE_MAP("a MutableMap", MAP),
        ;

        val readOnly: Kind = readOnly ?: this
    }
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
