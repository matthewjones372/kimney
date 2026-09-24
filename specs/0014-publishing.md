# 0014 — Ready to publish, not published

## Problem

kimney can only be used as a composite build: nothing produces the artifacts a
user's build would resolve, and there is no Gradle plugin marker for
`id("io.github.matthewjones372.kimney")` to find. Publishing itself needs
credentials and a signing key only the maintainer holds; everything short of
that can be built and checked here.

## Not doing

- **No release.** No upload to Maven Central or the Plugin Portal from here.
- **No Dokka.** The javadoc jar Central requires is empty for now; KDoc
  rendering is a separate decision about build time.

## Shape

- `kimney-runtime`, `kimney-derive` and `kimney-compiler-plugin` publish with
  POMs naming the licence, SCM and developer, using the same vanniktech
  plugin as pelican; sources jars are real, javadoc jars empty.
- `kimney-gradle-plugin` publishes its plugin marker, so a consumer resolves
  the plugin by id from a repository rather than an included build.
- Signing is required for a release version and skipped for a `-SNAPSHOT`, as
  pelican does, so `publishToMavenLocal` needs no key.
- A consumer check: `publishToMavenLocal`, then a scratch build that uses
  only `mavenLocal()` and the plugin id compiles and runs a transformation.

## Stack

- [ ] **`spec-0014-publish`** — publishing for all four artifacts; the
      consumer check; the README's release steps.
      Done when: the scratch consumer resolves everything from Maven Local and
      prints a transformed value.

## Acceptance

```bash
./gradlew publishToMavenLocal
```

## Decisions

- Drafted and committed on the maintainer's instruction to do the remaining
  work with the recommended answers: vanniktech as in pelican, empty javadoc
  jars, signing only for releases, nothing uploaded.
