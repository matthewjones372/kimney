import io.github.matthewjones372.kimney.transformInto

data class Pair2(val a: Int, val b: Int)
data class Pair2Dto(val a: Int, val b: Int)

var calls = 0

fun make(): Pair2 {
    calls++
    return Pair2(1, 2)
}

fun box(): String {
    val dto = make().transformInto<Pair2Dto>()
    return if (calls == 1 && dto == Pair2Dto(1, 2)) "OK" else "Fail: calls=$calls, $dto"
}
