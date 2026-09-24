package example.cookbook.results

import io.github.matthewjones372.kimney.transformInto

sealed interface Lookup<out T> {
    data class Found<T>(val value: T) : Lookup<T>

    data class Missing(val key: String) : Lookup<Nothing>
}

sealed interface LookupView<out T> {
    data class Found<T>(val value: T) : LookupView<T>

    data class Missing(val key: String) : LookupView<Nothing>
}

data class Product(val sku: String, val pence: Long)

data class ProductView(val sku: String, val pence: Long)

fun Lookup<Product>.toView(): LookupView<ProductView> = transformInto()
