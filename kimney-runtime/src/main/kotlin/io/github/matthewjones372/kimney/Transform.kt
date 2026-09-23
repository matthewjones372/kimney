package io.github.matthewjones372.kimney

/**
 * The compiler plugin replaces every call with the derived construction, so this body runs only without it.
 * Not `inline`: an inline body is copied into the caller, which ties every caller to this module's JVM target.
 */
public fun <B> Any?.transformInto(): B = throw KimneyNotApplied("transformInto")
