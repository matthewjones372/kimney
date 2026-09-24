package example.cookbook.overrides

import io.github.matthewjones372.kimney.into

data class Person(val fullName: String, val born: Int, val email: String)

data class PersonDto(val name: String, val age: Int, val email: String, val source: String)

fun toDto(person: Person, year: Int): PersonDto = person.into<_, PersonDto>()
    .withFieldRenamed(Person::fullName, PersonDto::name)
    .withFieldComputed(PersonDto::age) { year - it.born }
    .withFieldConst(PersonDto::source, "import")
    .transform()
