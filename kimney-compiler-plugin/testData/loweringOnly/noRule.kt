import io.github.matthewjones372.kimney.transformInto

fun root(n: Int): String = n.<!KIMNEY_INTERNAL_ERROR!>transformInto<String>()<!>
