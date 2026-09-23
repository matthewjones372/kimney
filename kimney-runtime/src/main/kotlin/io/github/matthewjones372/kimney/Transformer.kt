package io.github.matthewjones372.kimney

/** How one type becomes another, as a value. [Into.withTransformer] uses it for every nested pair it fits. */
public fun interface Transformer<in A, out B> {
    public fun transform(source: A): B
}
