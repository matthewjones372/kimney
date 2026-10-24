// RUN_PIPELINE_TILL: FRONTEND
import io.github.matthewjones372.kimney.Transformer
import io.github.matthewjones372.kimney.into

class Geo(val country: String)
class Address(val zip: String, val geo: Geo)
class Line(val sku: String)
class Person(val name: String, val address: Address, val billing: Address?, val lines: List<Line>)

data class GeoDto(val country: String)
data class AddressDto(val zip: String, val geo: GeoDto)
data class LineDto(val sku: String)
data class PersonDto(val name: String, val address: AddressDto, val billing: AddressDto?, val lines: List<LineDto>)

val addressToDto = Transformer<Address, AddressDto> { AddressDto(it.zip, GeoDto(it.geo.country)) }

fun valid(person: Person): PersonDto = person.into<_, PersonDto>()
    .withFieldConst({ it.address.zip }, "N1 9GU")
    .withFieldComputed({ it.address.geo.country }) { it.name.take(2) }
    .withFieldConst({ it.billing?.zip }, "unknown")
    .withFieldConst({ it.name }, "Ada")
    .transform()

fun notAParameter(person: Person): PersonDto = <!KIMNEY_CANNOT_TRANSFORM!>person.into<_, PersonDto>()
    .withFieldConst({ it.address.geo.country.length }, 2)
    .transform()<!>

fun throughAList(person: Person): PersonDto = <!KIMNEY_CANNOT_TRANSFORM!>person.into<_, PersonDto>()
    .withFieldConst({ it.lines.first().sku }, "X")
    .transform()<!>

fun crossesAList(person: Person): PersonDto = <!KIMNEY_CANNOT_TRANSFORM!>person.into<_, PersonDto>()
    .withFieldConst({ it.lines.size }, 0)
    .transform()<!>

fun underATransformer(person: Person): PersonDto = <!KIMNEY_CANNOT_TRANSFORM!>person.into<_, PersonDto>()
    .withTransformer(addressToDto)
    .withFieldConst({ it.address.zip }, "N1 9GU")
    .transform()<!>

fun wholeAndInside(person: Person): PersonDto = <!KIMNEY_CANNOT_TRANSFORM!>person.into<_, PersonDto>()
    .withFieldConst({ it.address }, AddressDto("N1", GeoDto("GB")))
    .withFieldConst({ it.address.zip }, "N1 9GU")
    .transform()<!>

fun notAChain(person: Person): PersonDto = <!KIMNEY_CANNOT_TRANSFORM!>person.into<_, PersonDto>()
    .withFieldConst({ it.address.zip.uppercase() }, "N1")
    .transform()<!>

fun nullThroughASafeCall(person: Person): PersonDto = <!KIMNEY_CANNOT_TRANSFORM!>person.into<_, PersonDto>()
    .withFieldComputed({ it.billing?.zip }) { null }
    .transform()<!>
