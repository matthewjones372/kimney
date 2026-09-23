import io.github.matthewjones372.kimney.transformInto

data class Source(val country: String)
data class Target(val country: String = "GB")

fun box(): String {
    val target: Target = Source("FR").transformInto()
    return if (target.country == "FR") "OK" else "Fail: $target"
}
