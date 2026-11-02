package kimney.benchmarks

import dev.surefold.kimney.Partial
import dev.surefold.kimney.transformInto
import dev.surefold.kimney.transformIntoPartial

// Each mapping as a kimney user writes it.

fun Order.toViewKimney(): OrderView = transformInto()

fun PlaceOrderRequest.toCommandKimney(): PlaceOrder = transformInto()

fun Customer.toDtoKimney(): CustomerDto = transformInto()

fun SignupForm.validateKimney(): Partial<Signup> = transformIntoPartial()
