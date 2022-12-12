package quizzly.author

object testdata:

  val title = "test quiz"
  val intro = "some intro"
  val lenMins = 60
  val curator = Person("cur", "curator name")
  val author1 = Person("author1", "author1 name")
  val author2 = Person("author2", "author2 name")
  val author3 = Person("author3", "author3 name")
  val authors = Set(author1, author2)
  val inspector1 = Person("inspector1", "inspector1 name")
  val inspector2 = Person("inspector2", "inspector2 name")
  val inspector3 = Person("inspector3", "inspector3 name")
  val inspectors = Set(inspector1, inspector2)

  val item = Item(
    "33",
    "item33",
    Statement("stmt33-1", Some("img33-1")),
    List(List(Statement("hint33-1", None))),
    true,
    List(1)
  )
  val section = Section("tq-1-1", "section title", "section intro", List(item))
