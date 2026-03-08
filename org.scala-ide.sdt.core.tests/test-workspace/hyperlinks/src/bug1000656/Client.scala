package bug1000656

/** Test that hyperlinking works for definitions defined in a dependent project. */
class Client {
  def foo: Unit = {
    val b: util.Box[Int] = null

    val t: b.myInt/*^*/ = 10

    val x = util.Full/*^*/("a")
  }
}
