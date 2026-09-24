package example.cookbook.transformers

import io.github.matthewjones372.kimney.Transformer
import io.github.matthewjones372.kimney.into

data class User(val fullName: String, val email: String)

data class UserDto(val name: String, val email: String)

data class Team(val lead: User, val members: List<User>, val motto: String?)

data class TeamDto(val lead: UserDto, val members: List<UserDto>, val motto: String)

/** How a User becomes a UserDto, written once, with kimney itself. */
val userToDto = Transformer<User, UserDto> {
    it.into<_, UserDto>().withFieldRenamed(User::fullName, UserDto::name).transform()
}

/** A null gets a value only where someone says which. */
val noMotto = Transformer<String?, String> { it ?: "(none)" }

fun toDto(team: Team): TeamDto = team.into<_, TeamDto>()
    .withTransformer(userToDto)
    .withTransformer(noMotto)
    .transform()
