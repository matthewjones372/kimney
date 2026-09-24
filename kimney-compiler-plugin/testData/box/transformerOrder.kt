import io.github.matthewjones372.kimney.Transformer
import io.github.matthewjones372.kimney.into

data class Inner(val v: String)
data class InnerDto(val v: String, val tag: String)
data class Outer(val inner: Inner)
data class OuterDto(val inner: InnerDto, val note: String)

val log = StringBuilder()

fun note(s: String): String {
    log.append(s)
    return s
}

fun box(): String {
    val dto = Outer(Inner(note("s"))).into<_, OuterDto>()
        .withFieldConst(OuterDto::note, note("c"))
        .withTransformer(note("t").let { Transformer<Inner, InnerDto> { i -> InnerDto(i.v, "x") } })
        .transform()
    return if (log.toString() == "sct" && dto == OuterDto(InnerDto("s", "x"), "c")) "OK" else "Fail: $log, $dto"
}
