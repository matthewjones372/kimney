package io.github.matthewjones372.kimney

/** The compiler plugin replaces every call with the derived construction, so this body runs only without it. */
public inline fun <reified B> Any?.transformInto(): B = throw KimneyNotApplied("transformInto")
