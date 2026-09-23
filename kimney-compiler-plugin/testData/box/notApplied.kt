import io.github.matthewjones372.kimney.KimneyNotApplied
import io.github.matthewjones372.kimney.transformInto

// Nothing lowers yet, so the stub is reached. Spec 0002 turns this into a transformation.
fun box(): String =
    try {
        1.transformInto<String>()
        "Fail: the stub returned"
    } catch (e: KimneyNotApplied) {
        "OK"
    }
