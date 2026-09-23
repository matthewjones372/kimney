import io.github.matthewjones372.kimney.transformInto

data class Box<A>(val value: A, val label: String)
data class BoxDto<A>(val value: A, val label: String)

fun box(): String {
    val dto = Box(42, "answer").transformInto<BoxDto<Int>>()
    return if (dto == BoxDto(42, "answer")) "OK" else "Fail: $dto"
}
