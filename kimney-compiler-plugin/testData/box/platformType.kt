// FILE: main.kt
import io.github.matthewjones372.kimney.transformInto

class Source(legacy: Legacy) {
    val name = legacy.name
}

data class Target(val name: String)

fun box(): String {
    val target = Source(Legacy()).transformInto<Target>()
    return if (target == Target("Ada")) "OK" else "Fail: $target"
}

// FILE: Legacy.java
public class Legacy {
    public String getName() { return "Ada"; }
}
