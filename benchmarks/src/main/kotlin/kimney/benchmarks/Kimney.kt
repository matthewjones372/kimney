package kimney.benchmarks

import io.github.matthewjones372.kimney.Partial
import io.github.matthewjones372.kimney.transformInto
import io.github.matthewjones372.kimney.transformIntoPartial

// Each mapping as a kimney user writes it.

fun Order.toViewKimney(): OrderView = transformInto()

fun PlaceOrderRequest.toCommandKimney(): PlaceOrder = transformInto()

fun Customer.toDtoKimney(): CustomerDto = transformInto()

fun SignupForm.validateKimney(): Partial<Signup> = transformIntoPartial()
