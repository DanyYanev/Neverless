package core

case class Amount (value: Int) extends AnyVal {
  def +(that: Amount): Amount = Amount(value + that.value)
  def -(that: Amount): Amount = Amount(value - that.value)
}
