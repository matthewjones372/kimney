package io.github.matthewjones372.kimney

public class KimneyNotApplied(call: String) : IllegalStateException(
    "$call reached runtime: the kimney compiler plugin did not replace this call. " +
        "Apply it with plugins { id(\"io.github.matthewjones372.kimney\") }.",
)
