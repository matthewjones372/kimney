import io.github.matthewjones372.kimney.Transformer
import io.github.matthewjones372.kimney.into

open class Person(val fullName: String)
class User(fullName: String) : Person(fullName)
data class UserDto(val name: String)

sealed interface Event {
    data class Joined(val who: User) : Event
    data object Left : Event
}
sealed interface EventDto {
    data class Joined(val who: UserDto) : EventDto
    data object Left : EventDto
}

data class Team(val lead: User, val members: List<User>, val motto: String?, val last: Event)
data class TeamDto(val lead: UserDto, val members: List<UserDto>, val motto: String, val last: EventDto)

var built = 0

fun personToDto(): Transformer<Person, UserDto> {
    built++
    return Transformer { UserDto(it.fullName.uppercase()) }
}

class OrEmpty : Transformer<String?, String> {
    override fun transform(source: String?): String = source ?: "none"
}

fun box(): String {
    val team = Team(User("ada"), listOf(User("bob"), User("cy")), null, Event.Joined(User("di")))
    val dto = team.into<_, TeamDto>()
        .withTransformer(personToDto())
        .withTransformer(OrEmpty())
        .transform()
    val expected = TeamDto(UserDto("ADA"), listOf(UserDto("BOB"), UserDto("CY")), "none", EventDto.Joined(UserDto("DI")))
    return when {
        dto != expected -> "Fail: $dto"
        built != 1 -> "Fail: the transformer was built $built times"
        else -> "OK"
    }
}
