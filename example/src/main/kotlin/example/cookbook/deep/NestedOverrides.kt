package example.cookbook.deep

import io.github.matthewjones372.kimney.into

data class Address(val street: String, val postcode: String)

data class Customer(val name: String, val address: Address, val billing: Address?)

data class AddressDto(val street: String, val zip: String, val country: String)

data class CustomerDto(val name: String, val address: AddressDto, val billing: AddressDto?)

fun Customer.toDto(country: String): CustomerDto = into<_, CustomerDto>()
    .withFieldComputed({ it.address.zip }) { it.address.postcode }
    .withFieldConst({ it.address.country }, country)
    .withFieldComputed({ it.billing?.zip }) { it.billing?.postcode.orEmpty() }
    .withFieldConst({ it.billing?.country }, country)
    .transform()
