package utils

import java.util.UUID

trait UUIDGenerator {
  def generateUUID(): UUID
}

object UUIDGeneratorImpl extends UUIDGenerator {
  override def generateUUID(): UUID = UUID.randomUUID()
}