import io.github.matthewjones372.kimney.transformInto

data object Empty
data object EmptyDto

data class Holder(val value: Empty)
data class HolderDto(val value: EmptyDto)

fun box(): String {
    val holder = Holder(Empty).transformInto<HolderDto>()
    return if (Empty.transformInto<EmptyDto>() === EmptyDto && holder.value === EmptyDto) "OK" else "Fail: $holder"
}
