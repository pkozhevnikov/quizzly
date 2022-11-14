package quizzly.school

import java.util.UUID

type PersonID = UUID

sealed trait Person(id: PersonID, name: String):
  def place: String
case class Official(id: PersonID, name: String) extends Person(id, name):
  override def place = "Official"
case class Student(id: PersonID, name: String) extends Person(id, name):
  override def place = "Student"
