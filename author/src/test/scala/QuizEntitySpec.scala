package quizzly.author

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import akka.persistence.testkit.scaladsl.{EventSourcedBehaviorTestKit as TestKit}

import org.scalatest.*
import org.scalatest.wordspec.AnyWordSpecLike

import com.typesafe.config.*

object QuizEntitySpec:
  val config: Config = ConfigFactory
    .parseString("""
      akka.actor {
        serialization-bindings {
          "quizzly.author.CborSerializable" = jackson-cbor
        }
      }
      """)
    .withFallback(TestKit.config)

class QuizEntitySpec
    extends ScalaTestWithActorTestKit(QuizEntitySpec.config),
      AnyWordSpecLike,
      BeforeAndAfterEach:

  val id = "tq-1"

  import Quiz.*

  private val kit = TestKit[Command, Event, Quiz](
    system,
    QuizEntity(id)
  )

  override protected def beforeEach(): Unit =
    super.beforeEach()
    kit.clear()

  val title = "test quiz"
  val intro = "some intro"
  val lenMins = 60
  val curator = Person("cur", "curator name")
  val author1 = Person("author1", "author1 name")
  val author2 = Person("author2", "author2 name")
  val authors = Set(author1, author2)
  val inspector1 = Person("inspector1", "inspector1 name")
  val inspector2 = Person("inspector2", "inspector2 name")
  val inspectors = Set(inspector1, inspector2)

  "Quiz entity" must {
    class E(val result: TestKit.CommandResultWithReply[Command, Event, Quiz, _], val clues: String*)
    def createRejected(
        error: Error,
        eres: E*
    ) =
      eres.foreach { r =>
        r.result.reply shouldBe Bad(error ++ r.clues)
        r.result.state shouldBe a[Blank]
        r.result.state.id shouldBe id
      }

    "reject any command if not exists" in {
      createRejected(Error(2010, "quiz not found"), E(kit.runCommand(Update("", "", 1, _)), id))
    }
    "be created" in {
      val result =
        kit.runCommand(Create(id, title, intro, curator, authors, inspectors, lenMins, _))
      result.reply shouldBe Good(CreateDetails(authors, inspectors))
      val state = result.stateOfType[Composing]
      result.state shouldBe a[Composing]
      result.state shouldBe Composing(id, title, intro, curator, authors, inspectors, lenMins)
    }
    "drops wrong authors and inspectors" in {
      val result = kit.runCommand(
        Create(
          id,
          title,
          intro,
          curator,
          authors + Person("abc", "abc name"),
          inspectors + Person("cde", "cde name"),
          lenMins,
          _
        )
      )
      result.reply shouldBe Good(CreateDetails(authors, inspectors))
      result.state shouldBe (Composing(id, title, intro, curator, authors, inspectors, lenMins))
    }
    "drops inspector if is in author list" in {
      val result =
        kit.runCommand(Create(id, title, intro, curator, authors, inspectors + author1, lenMins, _))
      result.reply shouldBe Good(CreateDetails(authors, inspectors))
      result.state shouldBe (Composing(id, title, intro, curator, authors, inspectors, lenMins))
    }

    def createDefault =
      val result = kit.runCommand(
        Create(id, title, intro, curator, authors + curator, inspectors + curator, lenMins, _)
      )
      result.reply shouldBe Good(CreateDetails(authors, inspectors))
      result

    "drops curator if is listed on authors or inspectors" in {
      val result = createDefault
      result.state shouldBe (Composing(id, title, intro, curator, authors, inspectors, lenMins))
    }
    "reject creation if already exists" in {
      createRejected(
        quizAlreadyExists,
        E(kit.runCommand(Create(id, title, intro, curator, authors, inspectors, lenMins, _)), id)
      )
    }
    "reject creation if few authors or inspectors" in {
      createRejected(
        notEnoughAuthors,
        E(kit.runCommand(Create(id, title, intro, curator, Set.empty, Set.empty, lenMins, _))),
        E(kit.runCommand(Create(id, title, intro, curator, authors, Set.empty, lenMins, _))),
        E(kit.runCommand(Create(id, title, intro, curator, Set.empty, inspectors, lenMins, _)))
      )
    }
    "reject creation if title short" in {
      createRejected(
        tooShortTitle,
        E(kit.runCommand(Create(id, "x", intro, curator, authors, inspectors, lenMins, _)))
      )
    }
    "reject creation if length short" in {
      createRejected(
        tooShortLength,
        E(
          kit.runCommand(Create(id, title, intro, curator, authors, inspectors, 1, _)),
          "1",
          "should be more than 3 minutes"
        ),
        E(
          kit.runCommand(Create(id, title, intro, curator, authors, inspectors, -1, _)),
          "-1",
          "should be more than 3 minutes"
        )
      )
    }
    "be updated" in {
      createDefault
      val update = kit.runCommand(Update("new title", "new intro", 70, _))
      update.reply shouldBe Resp.OK
      update.state shouldBe Composing(
        id,
        "new title",
        "new intro",
        curator,
        authors,
        inspectors,
        70
      )
    }
    "reject update if title short" in {
      val defState = createDefault.state
      val update = kit.runCommand(Update("x", intro, lenMins, _))
      update.reply shouldBe Bad(Error(2002, "too short title"))
      update.state shouldBe defState
    }
    "reject update if length short" in {
      val defState = createDefault.state
      val update1 = kit.runCommand(Update(title, intro, -1, _))
      update1.reply shouldBe Bad(tooShortLength + "-1" + "should be more than 3 minutes")
      val update2 = kit.runCommand(Update(title, intro, 1, _))
      update2.reply shouldBe Bad(tooShortLength + "1" + "should be more than 3 minutes")
    }
    "add an author" in {
      val defState = createDefault.stateOfType[Composing]
      val author = Person("new-auth", "new author name")
      val insert = kit.runCommand(AddAuthor(author, _))
      insert.reply shouldBe Resp.OK
      insert.state shouldBe defState.copy(authors = defState.authors + author)
    }
    "reject add author if already listed" in {
      val defState = createDefault.state
      def check(person: Person) =
        val insert = kit.runCommand(AddAuthor(person, _))
        insert.reply shouldBe Bad(alreadyOnList)
        insert.state shouldBe defState
      check(author1)
      check(inspector1)
      check(curator)
    }
    "reject add author if not official" in {
      val defState = createDefault.state
      val insert = kit.runCommand(AddAuthor(Person("not-exists", "the name"), _))
      insert.reply shouldBe Bad(notOfficial)
      insert.state shouldBe defState
    }
    "add an inspector" in {
      val defState = createDefault.stateOfType[Composing]
      val inspector = Person("new-insp", "new inspector name")
      val insert = kit.runCommand(AddInspector(inspector, _))
      insert.reply shouldBe Resp.OK
      insert.state shouldBe defState.copy(inspectors = defState.inspectors + inspector)
    }
    "reject add inspector if already listed" in {
      val defState = createDefault.state
      def check(person: Person) =
        val insert = kit.runCommand(AddInspector(person, _))
        insert.reply shouldBe Bad(alreadyOnList)
        insert.state shouldBe defState
      check(author1)
      check(inspector1)
      check(curator)
    }
    "reject add inspector if not official" in {
      val defState = createDefault.state
      val insert = kit.runCommand(AddInspector(Person("not-exists", "the name"), _))
      insert.reply shouldBe Bad(notOfficial)
      insert.state shouldBe defState
    }
    "remove author" in {
      val defState = createDefault.stateOfType[Composing]
      val remove = kit.runCommand(RemoveAuthor(author1, _))
      remove.reply shouldBe Resp.OK
      remove.state shouldBe defState.copy(authors = defState.authors - author1)
    }
    "reject remove author if not listed" in {
      val defState = createDefault.state
      val remove = kit.runCommand(RemoveAuthor(Person("not-exists", "the name"), _))
      remove.reply shouldBe Bad(notOnList)
      remove.state shouldBe defState
    }
    "remove inspector" in {
      val defState = createDefault.stateOfType[Composing]
      val remove = kit.runCommand(RemoveInspector(inspector1, _))
      remove.reply shouldBe Resp.OK
      remove.state shouldBe defState.copy(inspectors = defState.inspectors - inspector1)
    }
    "reject remove inspector if not listed" in {
      val defState = createDefault.state
      val remove = kit.runCommand(RemoveInspector(Person("not-exists", "the name"), _))
      remove.reply shouldBe Bad(notOnList)
      remove.state shouldBe defState
    }

    /*
    "add section" in {}
    "reject section creation if title not specified" in {}
    "reject section creation if quiz not found" in {}
    "reject section creation if not author" in {}
    "grab section" in {}
    "reject grab section if not author" in {}
    "reject grab section if section not found" in {}
    "save section" in {}
    "move section" in {}
    "not move section if at top or bottom" in {}
     */
  }
