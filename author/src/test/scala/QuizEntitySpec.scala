package quizzly.author

import akka.actor.testkit.typed.scaladsl.ActorTestKit
import akka.actor.typed.*
import akka.cluster.sharding.typed.scaladsl.EntityRef
import akka.cluster.sharding.typed.testkit.scaladsl.TestEntityRef
import akka.persistence.testkit.scaladsl.EventSourcedBehaviorTestKit as TestKit
import com.typesafe.config.*
import org.scalatest.*

import org.mockito.Mockito.*

import scala.concurrent.ExecutionContext

import scaladsl.Behaviors

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

class QuizEntitySpec extends wordspec.AnyWordSpec, matchers.should.Matchers, BeforeAndAfterEach:

  val testKit = ActorTestKit(QuizEntitySpec.config)
  val system = testKit.system

  val id = "tq-1"

  import Quiz.*
  import Resp.*
  import quizzly.school.{grpc => school}
  import quizzly.trial.{grpc => trial}

  private var sectionsm = scala.collection.mutable.Map.empty[SC, EntityRef[SectionEdit.Command]]
  private def putSection(id: SC, beh: Behavior[SectionEdit.Command]) =
    sectionsm += (id -> TestEntityRef(SectionEditEntity.EntityKey, id, testKit.spawn(beh)))

  given ExecutionContext = system.executionContext

  val schoolRegistry = mock(classOf[school.SchoolRegistry])
  val trialRegistry = mock(classOf[trial.Registry])

  private val kit = TestKit[Command, Event, Quiz](
    system,
    QuizEntity(
      id,
      sectionsm(_),
      schoolRegistry,
      trialRegistry,
      QuizConfig(
        minAuthors = 2,
        minInspectors = 2,
        minTrialLength = 3,
        minTitleLength = 2,
        inactivityMinutes = 1
      )
    )
  )

  override protected def beforeEach(): Unit =
    super.beforeEach()
    kit.clear()

  import testdata.*

  def createComposing =
    val result = kit.runCommand(Create(id, title, intro, curator, authors, inspectors, lenMins, _))
    result.reply shouldBe Good(CreateDetails(authors, inspectors))
    result

  extension (c: Composing)
    def signForReview =
      val result =
        c.authors
          .map { author =>
            kit.runCommand(SetReadySign(author, _))
          }
          .last
      result.state shouldBe a[Review]
      result

  extension (r: Review)
    def resolveForReleased =
      val result =
        r.composing
          .inspectors
          .map { inspector =>
            kit.runCommand(Resolve(inspector, true, _))
          }
          .last
      result.state shouldBe a[Released]
      result

  def rejected(e: Error, expectedState: AnyRef)(cmds: (ActorRef[Resp[?]] => Command)*) = cmds
    .foreach { cmd =>
      val result = kit.runCommand(cmd(_))
      result.reply shouldBe Bad(e)
      result.state shouldBe expectedState
    }

  "Quiz entity" when {

    "Blank" must {

      "reject any command except create" in {
        rejected(quizNotFound.error() + id, Blank(id))(
          Update("", "", 0, author1, _),
          AddInspector(curator, inspector1, _),
          AddAuthor(curator, author1, _),
          RemoveInspector(curator, inspector1, _),
          RemoveAuthor(curator, author1, _),
          AddSection("", author1, _),
          OwnSection("sc", author1, _),
          SaveSection(section, _),
          MoveSection("sc", false, author1, _),
          RemoveSection("sc", author1, _),
          SetReadySign(author1, _),
          UnsetReadySign(author1, _),
          Resolve(inspector1, false, _),
          SetObsolete(curator, _),
          Get(curator, _)
        )
      }

      "create" in {
        val result = kit
          .runCommand(Create(id, title, intro, curator, authors, inspectors, lenMins, _))
        result.reply shouldBe Good(CreateDetails(authors, inspectors))
        result.state shouldBe a[Composing]
        result.state shouldBe Composing(id, title, intro, curator, authors, inspectors, lenMins)
      }

      "drop inspector if listed as author" in {
        val result = kit
          .runCommand(Create(id, title, intro, curator, authors, inspectors + author1, lenMins, _))
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

      "reject creation if not enough authors" in {
        val result = kit
          .runCommand(Create(id, title, intro, curator, authors - author1, inspectors, lenMins, _))
        result.reply shouldBe Bad(notEnoughAuthors.error())
        result.state shouldBe Blank(id)
      }

      "reject creation if not enough inspectors" in {
        val result = kit.runCommand(
          Create(id, title, intro, curator, authors, inspectors - inspector1, lenMins, _)
        )
        result.reply shouldBe Bad(notEnoughInspectors.error())
        result.state shouldBe Blank(id)
      }

      "reject creation if title short" in {
        val result = kit
          .runCommand(Create(id, "x", intro, curator, authors, inspectors, lenMins, _))
        result.reply shouldBe Bad(tooShortTitle.error())
        result.state shouldBe Blank(id)
      }

      "reject creation if length short" in {
        val result = kit.runCommand(Create(id, title, intro, curator, authors, inspectors, 1, _))
        result.reply shouldBe Bad(tooShortLength.error())
        result.state shouldBe Blank(id)
      }

    }

    "Composing" must {

      "reject get if not member" in {
        createComposing
        val result = kit.runCommand(Get(Person("xyz", "not member"), _))
        result.reply shouldBe Bad(notMember.error())
        result.hasNoEvents shouldBe true
      }

      "return full view" in {
        val defState = createComposing.stateOfType[Composing]
        val result = kit.runCommand(Get(curator, _))
        result.hasNoEvents shouldBe true
        result.reply shouldBe
          Good(
            FullQuiz(
              defState.id,
              defState.title,
              defState.intro,
              curator,
              authors,
              inspectors,
              lenMins,
              Set.empty,
              Set.empty,
              Set.empty,
              false,
              List.empty,
              State.Composing
            )
          )
      }

      "reject creation if already exists" in {
        val defState = createComposing.stateOfType[Composing]
        val result = kit
          .runCommand(Create(id, title, intro, curator, authors, inspectors, lenMins, _))
        result.reply shouldBe Bad(quizAlreadyExists.error() + id)
        result.state shouldBe defState

        defState
          .authors
          .foreach { author =>
            val res = kit.runCommand(SetReadySign(author, _))
            res.reply shouldBe Resp.OK
          }
        val reviewRes = kit.runCommand(Create(id, "", "", curator, authors, inspectors, lenMins, _))
        reviewRes.reply shouldBe Bad(quizAlreadyExists.error() + id)
        reviewRes.state shouldBe a[Review]

        defState
          .inspectors
          .foreach { inspector =>
            val res = kit.runCommand(Resolve(inspector, true, _))
            res.reply shouldBe Resp.OK
          }
        val releaseRes = kit
          .runCommand(Create(id, "", "", curator, authors, inspectors, lenMins, _))
        releaseRes.reply shouldBe Bad(quizAlreadyExists.error() + id)
        releaseRes.state shouldBe a[Released]
      }

      "be updated" in {
        createComposing
        val update = kit.runCommand(Update("new title", "new intro", 70, author1, _))
        update.reply shouldBe Resp.OK
        update.state shouldBe
          Composing(id, "new title", "new intro", curator, authors, inspectors, 70)
      }

      "reject update if title short" in {
        val defState = createComposing.state
        val update = kit.runCommand(Update("x", intro, lenMins, author1, _))
        update.reply shouldBe Bad(tooShortTitle.error())
        update.state shouldBe defState
      }

      "reject update if length short" in {
        val defState = createComposing.state
        val update1 = kit.runCommand(Update(title, intro, -1, author1, _))
        update1.reply shouldBe Bad(tooShortLength.error())
        val update2 = kit.runCommand(Update(title, intro, 1, author1, _))
        update2.reply shouldBe Bad(tooShortLength.error())
        update2.state shouldBe defState
      }

      "reject update if not author" in {
        createComposing
        val result = kit.runCommand(Update("xyz", "", 50, curator, _))
        result.reply shouldBe Bad(notAuthor.error())
        result.hasNoEvents shouldBe true
        val result2 = kit.runCommand(Update("xyz", "", 50, inspector1, _))
        result2.reply shouldBe Bad(notAuthor.error())
        result2.hasNoEvents shouldBe true
      }

      "add an author" in {
        val defState = createComposing.stateOfType[Composing]
        val author = Person("new-auth", "new author name")
        val insert = kit.runCommand(AddAuthor(curator, author, _))
        insert.reply shouldBe Resp.OK
        insert.state shouldBe defState.copy(authors = defState.authors + author)
      }

      "reject add author if caller is not curator" in {
        createComposing
        val result = kit.runCommand(AddAuthor(inspector1, author1, _))
        result.reply shouldBe Bad(notCurator.error())
        result.hasNoEvents shouldBe true
      }

      "reject add author if already listed" in {
        val defState = createComposing.state
        def check(person: Person) =
          val insert = kit.runCommand(AddAuthor(curator, person, _))
          insert.reply shouldBe Bad(alreadyOnList.error())
          insert.state shouldBe defState
        check(author1)
        check(inspector1)
        check(curator)
      }

      "reject add inspector if caller is not curator" in {
        createComposing
        val result = kit.runCommand(AddInspector(author1, inspector1, _))
        result.reply shouldBe Bad(notCurator.error())
        result.hasNoEvents shouldBe true
      }

      "add an inspector" in {
        val defState = createComposing.stateOfType[Composing]
        val inspector = Person("new-insp", "new inspector name")
        val insert = kit.runCommand(AddInspector(curator, inspector, _))
        insert.reply shouldBe Resp.OK
        insert.state shouldBe defState.copy(inspectors = defState.inspectors + inspector)
      }

      "reject add inspector if already listed" in {
        val defState = createComposing.state
        def check(person: Person) =
          val insert = kit.runCommand(AddInspector(curator, person, _))
          insert.reply shouldBe Bad(alreadyOnList.error())
          insert.state shouldBe defState
        check(author1)
        check(inspector1)
        check(curator)
      }

      "remove author" in {
        val defState = createComposing.stateOfType[Composing]
        kit.runCommand(AddAuthor(curator, author3, _))
        val signed = kit.runCommand(SetReadySign(author1, _)).stateOfType[Composing]
        signed.readinessSigns shouldBe Set(author1)
        val remove = kit.runCommand(RemoveAuthor(curator, author1, _))
        remove.reply shouldBe Resp.OK
        remove.state shouldBe
          defState.copy(authors = defState.authors + author3 - author1, readinessSigns = Set.empty)
      }

      "set/unset ready sign" in {
        val defResult = createComposing
        kit.runCommand(SetReadySign(author1, _))
        val result = kit.runCommand(UnsetReadySign(author1, _))
        result.reply shouldBe Resp.OK
        result.state shouldBe defResult.state
      }

      "set ready sign and move to review" in {
        val defResult = createComposing
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

      "reject remove author if min authors exceeds" in {
        val composing = createComposing.stateOfType[Composing]
        val result = kit.runCommand(RemoveAuthor(curator, author1, _))
        result.reply shouldBe Bad(notEnoughAuthors.error())
        result.state shouldBe composing
      }

      "reject remove author if not listed" in {
        val defState = createComposing.state
        val remove = kit.runCommand(RemoveAuthor(curator, Person("not-exists", "the name"), _))
        remove.reply shouldBe Bad(notOnList.error())
        remove.state shouldBe defState
      }

      "reject remove author if caller is not curator" in {
        createComposing
        val result = kit.runCommand(RemoveAuthor(inspector1, author1, _))
        result.reply shouldBe Bad(notCurator.error())
        result.hasNoEvents shouldBe true
      }

      "remove inspector" in {
        val defState = createComposing.stateOfType[Composing]
        kit.runCommand(AddInspector(curator, inspector3, _))
        val remove = kit.runCommand(RemoveInspector(curator, inspector1, _))
        remove.reply shouldBe Resp.OK
        remove.state shouldBe
          defState.copy(inspectors = defState.inspectors + inspector3 - inspector1)
      }

      "reject remove inspector if not listed" in {
        val defState = createComposing.state
        val remove = kit.runCommand(RemoveInspector(curator, Person("not-exists", "the name"), _))
        remove.reply shouldBe Bad(notOnList.error())
        remove.state shouldBe defState
      }

      "reject remove inspector if min inspectors exceeds" in {
        val composing = createComposing.state
        val result = kit.runCommand(RemoveInspector(curator, inspector1, _))
        result.reply shouldBe Bad(notEnoughInspectors.error())
        result.state shouldBe composing
      }

      "reject remove inspector if caller is not curator" in {
        createComposing
        val result = kit.runCommand(RemoveInspector(author1, inspector1, _))
        result.reply shouldBe Bad(notCurator.error())
        result.hasNoEvents shouldBe true
      }

      "reject set ready sign if not an author" in {
        val defState = createComposing.state
        val result = kit.runCommand(SetReadySign(inspector1, _))
        result.reply shouldBe Bad(notAuthor.error())
        result.state shouldBe defState
      }

      "reject set ready sign if already signed" in {
        createComposing
        val result1 = kit.runCommand(SetReadySign(author1, _))
        result1.reply shouldBe Resp.OK
        result1.stateOfType[Composing].readinessSigns shouldBe Set(author1)
        val result2 = kit.runCommand(SetReadySign(author1, _))
        result2.reply shouldBe Bad(alreadySigned.error())
        result2.stateOfType[Composing].readinessSigns shouldBe Set(author1)
      }

      "reject other actions" in {
        val composing = createComposing.stateOfType[Composing]
        rejected(isComposing.error(), composing)(
          Resolve(inspector1, true, _),
          SetObsolete(curator, _)
        )
      }

      "reject add section if not author" in {
        val defState = createComposing.state
        val result = kit.runCommand(AddSection("s", inspector1, _))
        result.reply shouldBe Bad(notAuthor.error())
        result.hasNoEvents shouldBe true
        result.state shouldBe defState
      }

      "not add section as section doesn't respond" in {
        val defState = createComposing.stateOfType[Composing]
        putSection("tq-1-1", Behaviors.empty)
        val result = kit.runCommand(AddSection("s", author1, _))
        result.reply shouldBe Bad(timedOut.error())
        result.event shouldBe SCIncrement
        result.state shouldBe defState.copy(scCounter = 2)
      }

      "reject add section if already exists" in {
        val defState = createComposing.stateOfType[Composing]
        putSection(
          "tq-1-1",
          Behaviors.receiveMessage { case c: SectionEdit.Create =>
            c.replyTo ! Bad(SectionEdit.alreadyExists.error() + "tq-1-1")
            Behaviors.stopped
          }
        )
        val result = kit.runCommand(AddSection(section.title, author1, _))
        result.reply shouldBe Bad(SectionEdit.alreadyExists.error() + "tq-1-1")
        result.state shouldBe defState.copy(scCounter = 2)
      }

      def stdCreateSection = Behaviors.receiveMessage[SectionEdit.Command] {
        case c: SectionEdit.Create =>
          c.replyTo ! Resp.OK
          Behaviors.stopped
        case _ =>
          Behaviors.stopped
      }

      "add section" in {
        val defState = createComposing.stateOfType[Composing]
        putSection("tq-1-1", stdCreateSection)
        val result = kit.runCommand(AddSection(section.title, author1, _))
        result.reply shouldBe Good("tq-1-1")
        result.events shouldBe
          Seq(SCIncrement, SectionSaved(Section("tq-1-1", section.title, "", List.empty)))
        result.state shouldBe
          defState
            .copy(scCounter = 2, sections = List(Section("tq-1-1", section.title, "", List.empty)))
      }

      "save section" in {
        val defState = createComposing.stateOfType[Composing]
        putSection("tq-1-1", stdCreateSection)
        kit.runCommand(AddSection(section.title, author1, _))
        val result = kit.runCommand(SaveSection(section, _))
        result.reply shouldBe Good("tq-1-1")
        result.event shouldBe a[SectionSaved]
        result.state shouldBe defState.copy(scCounter = 2, sections = List(section))
      }

      "reject move section" in {
        val defState = createComposing.state
        putSection("tq-1-1", stdCreateSection)
        kit.runCommand(AddSection(section.title, author1, _))
        kit.runCommand(SaveSection(section, _))
        val result4 = kit.runCommand(MoveSection("t1-1-1", true, inspector1, _))
        result4.reply shouldBe Bad(notAuthor.error())
        result4.hasNoEvents shouldBe true
        val result = kit.runCommand(MoveSection("tq-1-1", true, author1, _))
        result.reply shouldBe Bad(cannotMove.error())
        result.hasNoEvents shouldBe true
        val result2 = kit.runCommand(MoveSection("tq-1-1", false, author1, _))
        result2.reply shouldBe Bad(cannotMove.error())
        result2.hasNoEvents shouldBe true
        val result3 = kit.runCommand(MoveSection("notexist", true, author1, _))
        result3.reply shouldBe Bad(sectionNotFound.error() + "notexist")
        result3.hasNoEvents shouldBe true
      }

      "move section" in {
        val defState = createComposing.stateOfType[Composing]
        putSection("tq-1-1", stdCreateSection)
        putSection("tq-1-2", stdCreateSection)
        kit.runCommand(AddSection(section.title, author1, _))
        kit.runCommand(SaveSection(section, _))
        val section2 = section.copy(sc = "tq-1-2", title = "section2")
        kit.runCommand(AddSection(section2.title, author1, _))
        val result0 = kit.runCommand(SaveSection(section2, _))
        result0.state shouldBe defState.copy(scCounter = 3, sections = List(section, section2))
        val result = kit.runCommand(MoveSection("tq-1-2", true, author1, _))
        result.reply shouldBe Good(List("tq-1-2", "tq-1-1"))
        result.state shouldBe defState.copy(scCounter = 3, sections = List(section2, section))

        val result2 = kit.runCommand(MoveSection("tq-1-2", false, author1, _))
        result2.reply shouldBe Good(List("tq-1-1", "tq-1-2"))
        result2.state shouldBe defState.copy(scCounter = 3, sections = List(section, section2))
      }

      "reject remove section not found" in {
        val defState = createComposing.state
        val result = kit.runCommand(RemoveSection("notexist", author1, _))
        result.reply shouldBe Bad(sectionNotFound.error() + "notexist")
        result.hasNoEvents shouldBe true
      }

      "reject remove section not author" in {
        val desTate = createComposing.state
        putSection("tq-1-1", stdCreateSection)
        kit.runCommand(AddSection(section.title, author1, _))
        kit.runCommand(SaveSection(section, _))
        val result = kit.runCommand(RemoveSection("tq-1-1", inspector1, _))
        result.reply shouldBe Bad(notAuthor.error())
        result.hasNoEvents shouldBe true
      }

      "reject remove section is owned" in {
        val defState = createComposing.state
        putSection(
          "tq-1-1",
          Behaviors.receiveMessage {
            case c: SectionEdit.Create =>
              c.replyTo ! Resp.OK
              Behaviors.same
            case c: SectionEdit.GetOwner =>
              c.replyTo ! Good(Some(author1))
              Behaviors.stopped
          }
        )
        kit.runCommand(AddSection(section.title, author1, _))
        val result = kit.runCommand(RemoveSection("tq-1-1", author1, _))
        result.reply shouldBe Bad(SectionEdit.alreadyOwned.error())
        result.hasNoEvents shouldBe true
      }

      "remove section" in {
        val defState = createComposing.stateOfType[Composing]
        putSection(
          "tq-1-1",
          Behaviors.receiveMessage {
            case c: SectionEdit.Create =>
              c.replyTo ! Resp.OK
              Behaviors.same
            case c: SectionEdit.GetOwner =>
              c.replyTo ! Good(None)
              Behaviors.stopped
          }
        )
        kit.runCommand(AddSection(section.title, author1, _))
        kit.runCommand(SaveSection(section, _))
        val result = kit.runCommand(RemoveSection("tq-1-1", author1, _))
        result.reply shouldBe Resp.OK
        result.state shouldBe defState.copy(scCounter = 2)
      }

      "reject own section if not found" in {
        createComposing
        val result = kit.runCommand(OwnSection("notexist", author1, _))
        result.reply shouldBe Bad(sectionNotFound.error() + "notexist")
      }

      "reject own section if not author" in {
        createComposing
        putSection("tq-1-1", stdCreateSection)
        kit.runCommand(AddSection(section.title, author1, _))
        val result = kit.runCommand(OwnSection("tq-1-1", inspector1, _))
        result.reply shouldBe Bad(notAuthor.error())
      }

      "reject own section if already owned" in {
        val defState = createComposing.stateOfType[Composing]
        putSection(
          "tq-1-1",
          Behaviors.receiveMessage {
            case c: SectionEdit.Create =>
              c.replyTo ! Resp.OK
              Behaviors.same
            case c: SectionEdit.Own =>
              c.replyTo ! Bad(SectionEdit.alreadyOwned.error() + author1.name)
              Behaviors.stopped
          }
        )
        kit.runCommand(AddSection(section.title, author1, _))
        val result = kit.runCommand(OwnSection("tq-1-1", author1, _))
        result.reply shouldBe Bad(SectionEdit.alreadyOwned.error() + author1.name)
      }

      "own section" in {
        createComposing
        putSection(
          "tq-1-1",
          Behaviors.receiveMessage {
            case c: SectionEdit.Create =>
              c.replyTo ! Resp.OK
              Behaviors.same
            case c: SectionEdit.Own =>
              c.replyTo ! Good(section)
              Behaviors.stopped
          }
        )
        kit.runCommand(AddSection(section.title, author1, _))
        val result = kit.runCommand(OwnSection("tq-1-1", author2, _))
        result.reply shouldBe Good(section)
      }

    }

    "Review" must {

      "reject get if not member" in {
        createComposing.stateOfType[Composing].signForReview
        val result = kit.runCommand(Get(Person("xyz", "not member"), _))
        result.reply shouldBe Bad(notMember.error())
        result.hasNoEvents shouldBe true
      }

      "return full view" in {
        val defState = createComposing.stateOfType[Composing].signForReview.stateOfType[Review]
        val result = kit.runCommand(Get(curator, _))
        result.hasNoEvents shouldBe true
        result.reply shouldBe
          Good(
            FullQuiz(
              defState.composing.id,
              defState.composing.title,
              defState.composing.intro,
              curator,
              authors,
              inspectors,
              lenMins,
              authors,
              Set.empty,
              Set.empty,
              false,
              List.empty,
              State.Review
            )
          )
      }

      "reject set ready sign" in {
        createComposing.stateOfType[Composing].signForReview
        kit.runCommand(AddAuthor(curator, author3, _))
        val failed = kit.runCommand(SetReadySign(author3, _))
        failed.reply shouldBe Bad(onReview.error())
        failed.stateOfType[Review].composing.readinessSigns should not contain author3
      }

      "reject unset ready sign" in {
        createComposing.stateOfType[Composing].signForReview
        val failed = kit.runCommand(UnsetReadySign(author1, _))
        failed.reply shouldBe Bad(onReview.error())
        failed.stateOfType[Review].composing.readinessSigns should contain theSameElementsAs
          Set(author1, author2)
      }

      def stdCreateSection = Behaviors.receiveMessage[SectionEdit.Command] {
        case c: SectionEdit.Create =>
          c.replyTo ! Resp.OK
          Behaviors.stopped
        case _ =>
          Behaviors.stopped
      }

      "resolve to release" in {
        reset(schoolRegistry)
        reset(trialRegistry)
        createComposing

        putSection("tq-1-1", stdCreateSection)
        kit.runCommand(AddSection(section.title, author1, _))
        val composing = kit.runCommand(SaveSection(section, _)).stateOfType[Composing]

        composing.signForReview
        val result1 = kit.runCommand(Resolve(inspector1, true, _))
        result1.reply shouldBe Resp.OK
        result1.state shouldBe
          Review(
            composing = composing.copy(readinessSigns = composing.authors),
            approvals = Set(inspector1),
            disapprovals = Set.empty
          )
        val result2 = kit.runCommand(Resolve(inspector2, true, _))
        result2.reply shouldBe Resp.OK
        result2.state shouldBe a[Released]
        result2.state shouldBe
          Released(
            composing.id,
            composing.title,
            composing.intro,
            composing.curator,
            composing.authors,
            composing.inspectors,
            composing.recommendedLength,
            List(section),
            false
          )
        verify(schoolRegistry).registerQuiz(
          school.RegisterQuizRequest(
            composing.id,
            composing.title,
            composing.intro,
            composing.recommendedLength,
            school.Person(composing.curator.id, composing.curator.name),
            composing
              .authors
              .map { a =>
                school.Person(a.id, a.name)
              }
              .toSeq,
            composing.inspectors.map(i => school.Person(i.id, i.name)).toSeq,
            Seq(
              school.Section(
                section.sc,
                section.title,
                section.intro,
                section
                  .items
                  .map { i =>
                    school.Item(
                      i.sc,
                      i.intro,
                      school.Statement(i.definition.text, i.definition.image),
                      i.hints
                        .map(h => school.Hint(h.map(s => school.Statement(s.text, s.image)).toSeq)),
                      i.hintsVisible,
                      i.solutions.toSeq
                    )
                  }
              )
            )
          )
        )

        val expected = trial.RegisterQuizRequest(
          "tq-1",
          "test quiz",
          "some intro",
          Seq(
            trial.Section(
              "tq-1-1",
              "section title",
              "section intro",
              Seq(
                trial.Item(
                  "33",
                  "item33",
                  trial.Statement("stmt33-1", Some("img33-1")),
                  Seq(trial.Hint(Seq(trial.Statement("hint33-1", None)))),
                  true,
                  Seq(1)
                )
              )
            )
          )
        )
        verify(trialRegistry).registerQuiz(expected)
      }

      "resolve to composing" in {
        val composing = createComposing.stateOfType[Composing]
        composing.signForReview
        val result1 = kit.runCommand(Resolve(inspector1, false, _))
        result1.reply shouldBe Resp.OK
        result1.state shouldBe
          Review(
            composing = composing.copy(readinessSigns = composing.authors),
            approvals = Set.empty,
            disapprovals = Set(inspector1)
          )
        val result2 = kit.runCommand(Resolve(inspector2, false, _))
        result2.reply shouldBe Resp.OK
        result2.state shouldBe a[Composing]
        result2.state shouldBe composing.copy(readinessSigns = Set.empty)
      }

      "reject resolve if not an inspector" in {
        val review = createComposing.stateOfType[Composing].signForReview.state
        val result = kit.runCommand(Resolve(curator, true, _))
        result.reply shouldBe Bad(notInspector.error())
        result.state shouldBe review
      }

      def reviewState = createComposing.stateOfType[Composing].signForReview.stateOfType[Review]

      "add author" in {
        val review = reviewState
        val result = kit.runCommand(AddAuthor(curator, author3, _))
        result.reply shouldBe Resp.OK
        result.state shouldBe
          review
            .copy(composing = review.composing.copy(authors = review.composing.authors + author3))
      }

      "reject add author if listed" in {
        val review = reviewState
        def rejected(p: Person) =
          val result = kit.runCommand(AddAuthor(curator, p, _))
          result.reply shouldBe Bad(alreadyOnList.error())
          result.state shouldBe review
        rejected(curator)
        rejected(author1)
        rejected(inspector1)
      }

      "reject add author if caller is not curator" in {
        reviewState
        val result = kit.runCommand(AddAuthor(inspector1, author1, _))
        result.reply shouldBe Bad(notCurator.error())
        result.hasNoEvents shouldBe true
      }

      "reject remove author if min authors exceeds" in {
        val review = reviewState
        val result = kit.runCommand(RemoveAuthor(curator, author1, _))
        result.reply shouldBe Bad(notEnoughAuthors.error())
        result.state shouldBe review
      }

      "reject remove author if caller is not curator" in {
        reviewState
        val result = kit.runCommand(RemoveAuthor(inspector1, author1, _))
        result.reply shouldBe Bad(notCurator.error())
        result.hasNoEvents shouldBe true
      }

      "remove author" in {
        createComposing
        val composing = kit.runCommand(AddAuthor(curator, author3, _)).stateOfType[Composing]
        composing.signForReview
        val remove = kit.runCommand(RemoveAuthor(curator, author3, _))
        remove.reply shouldBe Resp.OK
        remove.state shouldBe
          Review(
            composing.copy(
              authors = composing.authors - author3,
              readinessSigns = Set(author1, author2, author3)
            ),
            Set.empty,
            Set.empty
          )
      }

      "add inspector" in {
        val review = reviewState
        val result = kit.runCommand(AddInspector(curator, inspector3, _))
        result.reply shouldBe Resp.OK
        result.stateOfType[Review].composing.inspectors should contain theSameElementsAs
          Set(inspector1, inspector2, inspector3)
      }

      "reject add inspector if listed" in {
        val review = reviewState
        def rejected(p: Person) =
          val result = kit.runCommand(AddInspector(curator, p, _))
          result.reply shouldBe Bad(alreadyOnList.error())
          result.state shouldBe review
        rejected(author1)
        rejected(inspector1)
        rejected(curator)
      }

      "reject add inspector if caller is not curator" in {
        reviewState
        val result = kit.runCommand(AddInspector(author1, inspector1, _))
        result.reply shouldBe Bad(notCurator.error())
        result.hasNoEvents shouldBe true
      }

      "reject remove inspector if min inspectors exceeds" in {
        val review = reviewState
        val result = kit.runCommand(RemoveInspector(curator, inspector1, _))
        result.reply shouldBe Bad(notEnoughInspectors.error())
        result.state shouldBe review
      }

      "reject remove inspector if caller is not curator" in {
        reviewState
        val result = kit.runCommand(RemoveInspector(author1, inspector1, _))
        result.reply shouldBe Bad(notCurator.error())
        result.hasNoEvents shouldBe true
      }

      "remove inspector" in {
        reviewState
        kit.runCommand(AddInspector(curator, inspector3, _))
        val resolve = kit.runCommand(Resolve(inspector2, true, _))
        resolve.reply shouldBe Resp.OK
        resolve.stateOfType[Review].approvals shouldBe Set(inspector2)
        val remove = kit.runCommand(RemoveInspector(curator, inspector2, _))
        remove.reply shouldBe Resp.OK
        val review = remove.stateOfType[Review]
        review.composing.inspectors should contain theSameElementsAs Set(inspector1, inspector3)
        review.approvals shouldBe empty
      }

      "reject other actions" in {
        val review = createComposing.stateOfType[Composing].signForReview.state
        rejected(onReview.error(), review)(
          Update("", "", 1, author1, _),
          AddSection("", author1, _),
          OwnSection("sc", author1, _),
          SaveSection(section, _),
          MoveSection("sc", false, author1, _),
          RemoveSection("sc", author1, _),
          SetObsolete(curator, _)
        )
      }

    }

    "Released" must {

      def makeReleased =
        createComposing.stateOfType[Composing].signForReview.stateOfType[Review].resolveForReleased

      "reject get if not member" in {
        makeReleased
        val result = kit.runCommand(Get(Person("xyz", "not member"), _))
        result.reply shouldBe Bad(notMember.error())
        result.hasNoEvents shouldBe true
      }

      "return full view" in {
        makeReleased
        val defState = kit.runCommand(SetObsolete(curator, _)).stateOfType[Released]
        val result = kit.runCommand(Get(curator, _))
        result.hasNoEvents shouldBe true
        result.reply shouldBe
          Good(
            FullQuiz(
              defState.id,
              defState.title,
              defState.intro,
              curator,
              authors,
              inspectors,
              lenMins,
              Set.empty,
              Set.empty,
              Set.empty,
              true,
              List.empty,
              State.Released
            )
          )
      }

      "set obsolete" in {
        reset(schoolRegistry)
        val release = makeReleased
        release.stateOfType[Released].obsolete shouldBe false
        val result = kit.runCommand(SetObsolete(curator, _))
        result.reply shouldBe Resp.OK
        result.stateOfType[Released].obsolete shouldBe true
        verify(schoolRegistry).setQuizObsolete(school.SetObsoleteRequest("tq-1"))
      }

      "reject set obsolete if already set" in {
        makeReleased
        val result = kit.runCommand(SetObsolete(curator, _))
        result.reply shouldBe Resp.OK
        result.stateOfType[Released].obsolete shouldBe true
        val result2 = kit.runCommand(SetObsolete(curator, _))
        result2.reply shouldBe Bad(alreadyObsolete.error())
        result2.stateOfType[Released].obsolete shouldBe true
      }

      "reject creation" in {
        makeReleased
        val result = kit.runCommand(Create(id, "", "", curator, Set.empty, Set.empty, lenMins, _))
        result.reply shouldBe Bad(quizAlreadyExists.error() + id)
        result.state shouldBe a[Released]
      }

      "reject other actions" in {
        val released = makeReleased.state
        rejected(quizReleased.error(), released)(
          Update("", "", 1, author1, _),
          AddAuthor(curator, author1, _),
          RemoveAuthor(curator, author1, _),
          AddInspector(curator, inspector1, _),
          RemoveInspector(curator, inspector1, _),
          SetReadySign(author1, _),
          UnsetReadySign(author1, _),
          Resolve(inspector1, true, _),
          AddSection("", author1, _),
          OwnSection("", author1, _),
          SaveSection(section, _),
          MoveSection("sc", true, author1, _),
          RemoveSection("sc", author1, _)
        )
      }

    }

  }
