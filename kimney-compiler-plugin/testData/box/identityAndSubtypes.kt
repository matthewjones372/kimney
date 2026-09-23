import io.github.matthewjones372.kimney.transformInto

open class Animal(val name: String)
class Dog(name: String) : Animal(name)

data class Kennel(val resident: Dog, val tag: Int)
data class Shelter(val resident: Animal, val tag: Int)

fun box(): String {
    val dog = Dog("Rex")
    val shelter = Kennel(dog, 7).transformInto<Shelter>()
    return if (shelter.resident === dog && shelter.tag == 7) "OK" else "Fail: $shelter"
}
