package example.cookbook.optionals

import io.github.matthewjones372.kimney.transformInto

data class Address(val street: String)

data class Profile(val nickname: String, val billing: Address?)

data class AddressDto(val street: String)

data class ProfileDto(val nickname: String?, val billing: AddressDto?)

fun toDto(profile: Profile): ProfileDto = profile.transformInto<ProfileDto>()
