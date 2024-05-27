package utils

import java.util.UUID

object UUIDVar {
  def unapply(str: String): Option[UUID] = {
    try {
      Some(UUID.fromString(str))
    } catch {
      case _: IllegalArgumentException => None
    }
  }
}
