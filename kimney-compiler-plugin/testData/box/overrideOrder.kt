import io.github.matthewjones372.kimney.into

data class Source(val a: String)
data class Target(val c: String, val b: String, val a: String)

val log = StringBuilder()

fun note(s: String): String {
    log.append(s)
    return s
}

fun box(): String {
    val target = Source(note("s")).into<_, Target>()
        .withFieldConst(Target::b, note("b"))
        .withFieldComputed(Target::c) { note("c") }
        .transform()
    return if (log.toString() == "sbc" && target == Target("c", "b", "s")) "OK" else "Fail: $log, $target"
}
