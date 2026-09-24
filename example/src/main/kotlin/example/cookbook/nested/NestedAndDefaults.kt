package example.cookbook.nested

import io.github.matthewjones372.kimney.transformInto

data class Address(val street: String, val zip: String)

data class Customer(val name: String, val address: Address)

data class AddressDto(val street: String, val zip: String, val country: String = "GB")

data class CustomerDto(val name: String, val address: AddressDto, val tier: String = "standard")

fun Customer.toDto(): CustomerDto = transformInto()
