import io.github.matthewjones372.kimney.into

class Geo(val country: String)
class Address(val zip: String, val geo: Geo)
data class Tag(val label: String)
class Person(val name: String, val region: String, val address: Address, val billing: Address?, val tag: Tag)

data class GeoDto(val country: String)
data class AddressDto(val zip: String, val geo: GeoDto)
data class PersonDto(val name: String, val address: AddressDto, val billing: AddressDto?, val tag: Tag)

fun Person.toDto(): PersonDto = into<_, PersonDto>()
    .withFieldConst({ it.address.zip }, "N1 9GU")
    .withFieldComputed({ it.address.geo.country }) { it.region.uppercase() }
    .withFieldConst({ it.billing?.zip }, "billing")
    .withFieldConst({ it.tag.label }, "rebuilt")
    .transform()

// Overrides are evaluated once each, in the order written, whatever their depth, before anything is built.
fun Person.ordered(log: MutableList<String>): PersonDto = into<_, PersonDto>()
    .withFieldComputed({ it.tag.label }) { log += "tag"; "t" }
    .withFieldComputed({ it.address.geo.country }) { log += "country"; "c" }
    .withFieldComputed({ it.billing?.zip }) { log += "billing"; "b" }
    .withFieldComputed({ it.address.zip }) { log += "zip"; "z" }
    .transform()

fun box(): String {
    val ada = Person("Ada", "gb", Address("old", Geo("xx")), Address("old", Geo("yy")), Tag("same"))
    val dto = ada.toDto()
    val expected = PersonDto(
        "Ada",
        AddressDto("N1 9GU", GeoDto("GB")),
        AddressDto("billing", GeoDto("yy")),
        Tag("rebuilt"),
    )
    if (dto != expected) return "Fail: $dto"

    val noBilling = Person("Bo", "fr", Address("old", Geo("xx")), null, Tag("same")).toDto()
    if (noBilling.billing != null) return "Fail: billing ${noBilling.billing}"
    if (noBilling.address != AddressDto("N1 9GU", GeoDto("FR"))) return "Fail: ${noBilling.address}"

    val log = mutableListOf<String>()
    Person("Cy", "de", Address("old", Geo("xx")), null, Tag("same")).ordered(log)
    if (log != listOf("tag", "country", "billing", "zip")) return "Fail: order $log"
    return "OK"
}
