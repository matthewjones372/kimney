// RUN_PIPELINE_TILL: FRONTEND
import io.github.matthewjones372.kimney.Transformer
import io.github.matthewjones372.kimney.into

open class Person(val fullName: String)
class User(fullName: String) : Person(fullName)
data class UserDto(val name: String)
data class Team(val lead: User, val members: List<User>, val motto: String?)
data class TeamDto(val lead: UserDto, val members: List<UserDto>, val motto: String)

val userToDto = Transformer<User, UserDto> { UserDto(it.fullName) }
val personToDto = Transformer<Person, UserDto> { UserDto(it.fullName) }
val orEmpty = Transformer<String?, String> { it.orEmpty() }
val neverUsed = Transformer<Int, Long> { it.toLong() }

class Named : Transformer<User, UserDto> {
    override fun transform(source: User): UserDto = UserDto(source.fullName)
}

fun valid(team: Team): TeamDto = team.into<_, TeamDto>()
    .withTransformer(userToDto)
    .withTransformer(orEmpty)
    .transform()

fun byClass(team: Team): TeamDto = team.into<_, TeamDto>().withTransformer(Named()).withTransformer(orEmpty).transform()

fun ambiguous(team: Team): TeamDto = <!KIMNEY_CANNOT_TRANSFORM!>team.into<_, TeamDto>()
    .withTransformer(userToDto)
    .withTransformer(orEmpty)
    .withTransformer(personToDto)
    .transform()<!>

fun unused(team: Team): TeamDto = <!KIMNEY_UNUSED_TRANSFORMER!>team.into<_, TeamDto>()
    .withTransformer(userToDto)
    .withTransformer(orEmpty)
    .withTransformer(neverUsed)<!>
    .transform()
