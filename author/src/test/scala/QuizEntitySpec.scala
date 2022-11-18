package quizzly.author

import akka.actor.typed.ActorRef
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
    QuizEntity(
      id,
      QuizConfig(minAuthors = 2, minInspectors = 2, minTrialLength = 3, minTitleLength = 2)
    )
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
  val author3 = Person("author3", "author3 name")
  val authors = Set(author1, author2)
  val inspector1 = Person("inspector1", "inspector1 name")
  val inspector2 = Person("inspector2", "inspector2 name")
  val inspector3 = Person("inspector3", "inspector3 name")
  val inspectors = Set(inspector1, inspector2)

  "Quiz entity" must {

    "reject any command if not exists" in {
      def rejected(cmds: (ActorRef[Resp[_]] => Command)*) =
        cmds.foreach { cmd =>
          val result = kit.runCommand(cmd(_))
          result.reply shouldBe Bad(quizNotFound + id)
          result.state shouldBe Blank(id)
        }

      rejected(
        Update("", "", 0, _),
        AddInspector(inspector1, _),
        AddAuthor(author1, _),
        RemoveInspector(inspector1, _),
        RemoveAuthor(author1, _),
        AddSection("", author1, _),
        GrabSection("sc", author1, _),
        DischargeSection("sc", author1, _),
        MoveSection("sc", false, _),
        RemoveSection("sc", _),
        SetReadySign(author1, _),
        Resolve(inspector1, false, _),
        SetObsolete(_)
      )
    }
    "be created" in {
      val result =
        kit.runCommand(Create(id, title, intro, curator, authors, inspectors, lenMins, _))
      result.reply shouldBe Good(CreateDetails(authors, inspectors))
      result.state shouldBe a[Composing]
      result.state shouldBe Composing(id, title, intro, curator, authors, inspectors, lenMins)
    }
    "drop inspector if listed as author" in {
      val result =
        kit.runCommand(Create(id, title, intro, curator, authors, inspectors + author1, lenMins, _))
      result.reply shouldBe Good(CreateDetails(authors, inspectors))
      result.state shouldBe (Composing(id, title, intro, curator, authors, inspectors, lenMins))
    }

    "drop curator from author and inspector lists" in {
      val result = kit.runCommand(
        Create(id, title, intro, curator, authors + curator, inspectors + curator, lenMins, _)
      )
      result.reply shouldBe Good(CreateDetails(authors, inspectors))
      result.state shouldBe (Composing(id, title, intro, curator, authors, inspectors, lenMins))
    }
    "reject creation if already exists" ignore {
      val defState = createDefault.stateOfType[Composing]
      val result =
        kit.runCommand(Create(id, title, intro, curator, authors, inspectors, lenMins, _))
      result.reply shouldBe Bad(quizAlreadyExists + id)
      result.state shouldBe defState

      defState.authors.foreach { author =>
        val res = kit.runCommand(SetReadySign(author, _))
        res.reply shouldBe Resp.OK
      }
      val reviewRes = kit.runCommand(Create(id, "", "", curator, authors, inspectors, lenMins, _))
      reviewRes.reply shouldBe Bad(quizAlreadyExists + id)
      reviewRes.state shouldBe a[Review]

      defState.inspectors.foreach { inspector =>
        val res = kit.runCommand(Resolve(inspector, true, _))
        res.reply shouldBe Resp.OK
      }
      val releaseRes = kit.runCommand(Create(id, "", "", curator, authors, inspectors, lenMins, _))
      releaseRes.reply shouldBe Bad(quizAlreadyExists + id)
      releaseRes.state shouldBe a[Released]
    }
    "reject creation if not enough authors" in {
      val result =
        kit.runCommand(Create(id, title, intro, curator, authors - author1, inspectors, lenMins, _))
      result.reply shouldBe Bad(notEnoughAuthors)
      result.state shouldBe Blank(id)
    }
    "reject creation if not enough inspectors" in {
      val result = kit.runCommand(
        Create(id, title, intro, curator, authors, inspectors - inspector1, lenMins, _)
      )
      result.reply shouldBe Bad(notEnoughInspectors)
      result.state shouldBe Blank(id)
    }

    "reject creation if title short" in {
      val result = kit.runCommand(Create(id, "x", intro, curator, authors, inspectors, lenMins, _))
      result.reply shouldBe Bad(tooShortTitle)
      result.state shouldBe Blank(id)
    }
    "reject creation if length short" in {
      val result = kit.runCommand(Create(id, title, intro, curator, authors, inspectors, 1, _))
      result.reply shouldBe Bad(tooShortLength)
      result.state shouldBe Blank(id)
    }

    def createDefault = {
      val result =
        kit.runCommand(Create(id, title, intro, curator, authors, inspectors, lenMins, _))
      result.reply shouldBe Good(CreateDetails(authors, inspectors))
      result
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
      update1.reply shouldBe Bad(tooShortLength)
      val update2 = kit.runCommand(Update(title, intro, 1, _))
      update2.reply shouldBe Bad(tooShortLength)
      update2.state shouldBe defState
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
    "remove author when composing" in {
      val defState = createDefault.stateOfType[Composing]
      kit.runCommand(AddAuthor(author3, _))
      val signed = kit.runCommand(SetReadySign(author1, _)).stateOfType[Composing]
      signed.readinessSigns shouldBe Set(author1)
      val remove = kit.runCommand(RemoveAuthor(author1, _))
      remove.reply shouldBe Resp.OK
      remove.state shouldBe defState.copy(
        authors = defState.authors + author3 - author1,
        readinessSigns = Set.empty
      )
    }
    "remove author when review" in {
      createDefault
      val composing = kit.runCommand(AddAuthor(author3, _)).stateOfType[Composing]
      composing.authors.foreach { author => kit.runCommand(SetReadySign(author, _)) }
      val remove = kit.runCommand(RemoveAuthor(author3, _))
      remove.reply shouldBe Resp.OK
      remove.state shouldBe Review(
        composing.copy(authors = composing.authors - author3, readinessSigns = Set(author1, author2, author3)),
        Set.empty,
        Set.empty
      )
    }
    "reject remove author if min authors exceeds" in {
      val composing = createDefault.stateOfType[Composing]
      val result = kit.runCommand(RemoveAuthor(author1, _))
      result.reply shouldBe Bad(notEnoughAuthors)
      result.state shouldBe composing
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

    "set ready sign" in {
      val defResult = createDefault
      val result1 = kit.runCommand(SetReadySign(author1, _))
      result1.reply shouldBe Resp.OK
      result1.state shouldBe a[Composing]
      result1.state shouldBe defResult.stateOfType[Composing].copy(readinessSigns = Set(author1))
      val result2 = kit.runCommand(SetReadySign(author2, _))
      result2.reply shouldBe Resp.OK
      result2.state shouldBe a[Review]
      val review = result2.stateOfType[Review]
      review.composing.readinessSigns shouldBe Set(author1, author2)
    }
    "reject set ready sign if not an author" in {
      val defState = createDefault.state
      val result = kit.runCommand(SetReadySign(inspector1, _))
      result.reply shouldBe Bad(notAuthor)
      result.state shouldBe defState
    }
    "reject set ready sign if already signed" in {
      createDefault
      val result1 = kit.runCommand(SetReadySign(author1, _))
      result1.reply shouldBe Resp.OK
      result1.stateOfType[Composing].readinessSigns shouldBe Set(author1)
      val result2 = kit.runCommand(SetReadySign(author1, _))
      result2.reply shouldBe Bad(alreadySigned)
      result2.stateOfType[Composing].readinessSigns shouldBe Set(author1)
    }
    "reject set ready sign if not composing" in {
      val defState = createDefault.stateOfType[Composing]
      defState.authors.foreach { author =>
        val res = kit.runCommand(SetReadySign(author, _))
        res.reply shouldBe Resp.OK
      }
      kit.runCommand(AddAuthor(author3, _))
      val failed = kit.runCommand(SetReadySign(author3, _))
      failed.reply shouldBe Bad(onReview)
      failed.stateOfType[Review].composing.readinessSigns should not contain author3
    }

    "resolve to release" in {
      val composing = createDefault.stateOfType[Composing]
      composing.authors.foreach { author => kit.runCommand(SetReadySign(author, _)) }
      val result1 = kit.runCommand(Resolve(inspector1, true, _))
      result1.reply shouldBe Resp.OK
      result1.state shouldBe Review(
        composing = composing.copy(readinessSigns = composing.authors),
        approvals = Set(inspector1),
        disapprovals = Set.empty
      )
      val result2 = kit.runCommand(Resolve(inspector2, true, _))
      result2.reply shouldBe Resp.OK
      result2.state shouldBe a[Released]
      result2.state shouldBe Released(
        composing.id,
        composing.title,
        composing.intro,
        composing.curator,
        composing.authors,
        composing.inspectors,
        composing.recommendedLength,
        List.empty,
        false
      )
    }
    "resolve to composing" in {
      val composing = createDefault.stateOfType[Composing]
      composing.authors.foreach { author => kit.runCommand(SetReadySign(author, _)) }
      val result1 = kit.runCommand(Resolve(inspector2, false, _))
      result1.reply shouldBe Resp.OK
      result1.state shouldBe Review(
        composing = composing.copy(readinessSigns = composing.authors),
        approvals = Set.empty,
        disapprovals = Set(inspector2)
      )
      val result2 = kit.runCommand(Resolve(inspector2, false, _))
      result2.reply shouldBe Resp.OK
      result2.state shouldBe a[Composing]
      result2.state shouldBe Composing(
        composing.id,
        composing.title,
        composing.intro,
        composing.curator,
        composing.authors,
        composing.inspectors,
        composing.recommendedLength,
        Set.empty
      )
    }
    "reject resolve if not an inspector" in {
      val composing = createDefault.stateOfType[Composing]
      composing.authors.foreach { author => kit.runCommand(SetReadySign(author, _)) }
      val result = kit.runCommand(Resolve(curator, true, _))
      result.reply shouldBe Bad(notInspector)
      result.state shouldBe composing
    }
    "reject resolve if not on review" in {
      val composing = createDefault.stateOfType[Composing]
      val result = kit.runCommand(Resolve(inspector2, true, _))
      result.reply shouldBe Bad(onReview)
      result.state shouldBe composing
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
