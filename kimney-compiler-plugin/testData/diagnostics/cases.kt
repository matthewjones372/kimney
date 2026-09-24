// RUN_PIPELINE_TILL: FRONTEND
import io.github.matthewjones372.kimney.transformInto

enum class Status { ACTIVE, SUSPENDED, ARCHIVED }
enum class StatusDto { ACTIVE, SUSPENDED, UNKNOWN }
enum class Wide { ACTIVE, SUSPENDED, ARCHIVED, UNKNOWN }

sealed interface Shape {
    data class Circle(val radius: Int) : Shape
    data class Hexagon(val side: Double) : Shape
    data object Empty : Shape
}

sealed interface ShapeDto {
    data class Circle(val radius: Double) : ShapeDto
    data object Empty : ShapeDto
}

data class Account(val status: Status)
data class AccountDto(val status: StatusDto)

data object Nothing1
data class Something(val x: Int)

fun wide(status: Status): Wide = status.transformInto<Wide>()

fun narrow(status: Status): StatusDto = <!KIMNEY_CANNOT_TRANSFORM!>status.transformInto<StatusDto>()<!>

fun shape(shape: Shape): ShapeDto = <!KIMNEY_CANNOT_TRANSFORM!>shape.transformInto<ShapeDto>()<!>

fun account(account: Account): AccountDto = <!KIMNEY_CANNOT_TRANSFORM!>account.transformInto<AccountDto>()<!>

fun objectFromData(something: Something): Nothing1 = <!KIMNEY_CANNOT_TRANSFORM!>something.transformInto<Nothing1>()<!>

fun crossing(status: Status): ShapeDto = <!KIMNEY_CANNOT_TRANSFORM!>status.transformInto<ShapeDto>()<!>

sealed interface Figure {
    data class Circle(val radius: Double) : Figure
    data class Oval(val width: Double) : Figure
}

fun circle(c: Figure.Circle): ShapeDto = c.transformInto<ShapeDto>()

fun oval(o: Figure.Oval): ShapeDto = <!KIMNEY_CANNOT_TRANSFORM!>o.transformInto<ShapeDto>()<!>
