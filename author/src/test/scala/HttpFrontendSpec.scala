package quizzly.author

import akka.actor.typed.*
import scaladsl.Behaviors
import akka.cluster.sharding.typed.scaladsl.EntityRef
import akka.cluster.sharding.typed.testkit.scaladsl.TestEntityRef

import akka.http.scaladsl.model.*
import akka.http.scaladsl.marshalling.*
import akka.http.scaladsl.testkit.ScalatestRouteTest

import akka.actor.testkit.typed.scaladsl.ActorTestKit

import org.scalatest.*

import scala.concurrent.{ExecutionContext, Future}

class HttpFrontendSpec
    extends wordspec.AnyWordSpec,
      ScalatestRouteTest,
      JsonFormats,
      matchers.should.Matchers:

  val log = org.slf4j.LoggerFactory.getLogger("HttpFrontendSpec")

  val testKit = ActorTestKit("frontendkit")
  given ActorSystem[_] = testKit.system

  val p1 = Person("p1", "p1 name")
  val p2 = Person("p2", "p2 name")
  val p3 = Person("p3", "p3 name")
  val p4 = Person("p4", "p4 name")
  val p5 = Person("p5", "p5 name")

  val persons = Map("p1" -> p1, "p2" -> p2, "p3" -> p3, "p4" -> p4, "p5" -> p5)

  val fullquiz = FullQuiz(
    id = "q1",
    title = "q1 title",
    intro = "",
    curator = p1,
    authors = Set(p2, p3),
    inspectors = Set(p4, p5),
    recommendedLength = 70,
    readinessSigns = Set(p2, p3),
    approvals = Set(p4),
    disapprovals = Set(p5),
    obsolete = false,
    sections = List(
      Section(
        "q1-1",
        "q1-1 title",
        "q1-1 intro",
        List(
          Item(
            sc = "1",
            intro = "item intro",
            definition = Statement("item text", None),
            hints = List(List(Statement("hint 1", None), Statement("hint 2", None))),
            hintsVisible = true,
            solutions = List(0)
          )
        )
      )
    ),
    state = Quiz.State.COMPOSING
  )

  object auth extends Auth:
    def authenticate(req: HttpRequest) =
      val pl = req.getHeader("pl").get.value
      Future(persons(pl))
    def getPersons = Future(persons.values.toSet)
    def getPersons(ids: Set[PersonID]) = Future(persons.filter((k, v) => ids(k)).values.toSet)

  val emptyQuiz =
    (id: String) => TestEntityRef(QuizEntity.EntityKey, id, testKit.spawn(Behaviors.empty))
  val emptySect =
    (id: String) => TestEntityRef(SectionEditEntity.EntityKey, id, testKit.spawn(Behaviors.empty))

  object eaware extends EntityAware:
    def quiz(id: String) = emptyQuiz(id)
    def section(id: String) = emptySect(id)

  def quiz[R](expectedId: QuizID, resp: Resp[R]) =
    new EntityAware:
      val beh = Behaviors.receiveMessage[Quiz.Command] { case c: Quiz.CommandWithReply[R] =>
        c.replyTo ! resp
        Behaviors.stopped
      }
      def quiz(id: String) =
        id shouldBe expectedId
        TestEntityRef(QuizEntity.EntityKey, id, testKit.spawn(beh))
      def section(id: String) = emptySect(id)

  def quiz(expectedId: QuizID, beh: Behavior[Quiz.Command]) =
    new EntityAware:
      def quiz(id: QuizID) =
        id shouldBe expectedId
        TestEntityRef(QuizEntity.EntityKey, id, testKit.spawn(beh))
      def section(id: SC) = emptySect(id)

  def sect[R](expectedId: SC, resp: Resp[R]) =
    new EntityAware:
      val beh = Behaviors.receiveMessage[SectionEdit.Command] {
        case c: SectionEdit.CommandWithReply[R] =>
          c.replyTo ! resp
          Behaviors.stopped
      }
      def quiz(id: QuizID) = emptyQuiz(id)
      def section(id: SC) =
        id shouldBe expectedId
        TestEntityRef(SectionEditEntity.EntityKey, id, testKit.spawn(beh))
  def sect(expectedId: SC, beh: Behavior[SectionEdit.Command]) =
    new EntityAware:
      def quiz(id: QuizID) = emptyQuiz(id)
      def section(id: SC) =
        id shouldBe expectedId
        TestEntityRef(SectionEditEntity.EntityKey, id, testKit.spawn(beh))

  val quizList = List(
    QuizListed("q1", "quiz1", false, p1, Set(p1, p2), Set(p3, p4), Quiz.State.COMPOSING)
  )

  object read extends Read:
    def getList() = Future(quizList)

  def get(path: String, personId: PersonID) = Get(s"/v1/$path") ~> addHeader("pl", personId)
  def delete(path: String, personId: PersonID) = Delete(s"/v1/$path") ~> addHeader("pl", personId)
  def head(path: String, personId: PersonID) = Head(s"/v1/$path") ~> addHeader("pl", personId)
  def post[C](path: String, personId: PersonID, content: C)(using ToEntityMarshaller[C]) =
    Post(s"/v1/$path", content) ~> addHeader("pl", personId)
  def put[C](path: String, personId: PersonID, content: C)(using ToEntityMarshaller[C]) =
    Put(s"/v1/$path", content) ~> addHeader("pl", personId)
  def patch[C](path: String, personId: PersonID, content: C)(using ToEntityMarshaller[C]) =
    Patch(s"/v1/$path", content) ~> addHeader("pl", personId)
  def patch(path: String, personId: PersonID) = Patch(s"/v1/$path") ~> addHeader("pl", personId)

  def stdquiz[R](expectedId: QuizID, resp: Resp[R]) = HttpFrontend(
    read,
    quiz(expectedId, resp),
    auth
  )
  def spcquiz(expectedId: QuizID, beh: Behavior[Quiz.Command]) = HttpFrontend(
    read,
    quiz(expectedId, beh),
    auth
  )
  def stdsect[R](expectedId: SC, resp: Resp[R]) = HttpFrontend(read, sect(expectedId, resp), auth)
  def spcsect(expectedId: SC, beh: Behavior[SectionEdit.Command]) = HttpFrontend(
    read,
    sect(expectedId: SC, beh),
    auth
  )

  import Resp.*

  "quiz" when {
    "GET" should {
      "not authenticate" in {
        get("quiz", "x") ~> HttpFrontend(read, eaware, auth) ~>
          check {
            status shouldBe StatusCodes.Unauthorized
          }
      }
      "return list of quizzes" in {
        get("quiz", p1.id) ~> HttpFrontend(read, eaware, auth) ~>
          check {
            status shouldBe StatusCodes.OK
            responseAs[List[QuizListed]] shouldBe quizList
          }
      }
    }
    "POST" should {
      val create = CreateQuiz("q1", "q1 title", "q1 intro", 10, Set("p1", "p3"), Set("p4", "p5"))
      "create quiz" in {
        val auths = Set(p1, p3)
        val insps = Set(p4, p5)
        val crdet = Quiz.CreateDetails(auths, insps)
        val route = spcquiz(
          "q1",
          Behaviors.receiveMessage {
            case Quiz.Create("q1", "q1 title", "q1 intro", `p2`, `auths`, `insps`, 10, replyTo) =>
              replyTo ! Good(crdet)
              Behaviors.stopped
            case x =>
              fail(s"received wrong command $x")
              Behaviors.stopped
          }
        )
        post("quiz", p2.id, create) ~> route ~>
          check {
            status shouldBe StatusCodes.OK
            responseAs[Quiz.CreateDetails] shouldBe crdet
          }
      }
      "not create quiz with bad input" in {
        post("quiz", p2.id, create) ~> stdquiz("q1", Bad(Quiz.tooShortTitle.error())) ~>
          check {
            status shouldBe StatusCodes.UnprocessableEntity
            responseAs[Error] shouldBe Quiz.tooShortTitle.error()
          }
      }
    }
  }

  "staff" when {
    "GET" should {
      "return list of persons" in {
        get("staff", p1.id) ~> HttpFrontend(read, eaware, auth) ~>
          check {
            responseAs[Set[Person]] shouldBe persons.values.toSet
          }
      }
    }
  }

  "quiz/{id}" when {
    "GET" should {
      "return full quiz" in {
        get("quiz/q1", p1.id) ~> stdquiz("q1", Good(fullquiz)) ~>
          check {
            status shouldBe StatusCodes.OK
            responseAs[FullQuiz] shouldBe fullquiz
          }
      }
      "not return full quiz with error" in {
        get("quiz/q1", p1.id) ~> stdquiz("q1", Bad(Quiz.notMember.error())) ~>
          check {
            status shouldBe StatusCodes.UnprocessableEntity
            responseAs[Error] shouldBe Quiz.notMember.error()
          }
      }
    }
    "PUT" should {
      val updt = UpdateQuiz("q1 title+", "q1 intro+", 55)
      "update quiz" in {
        val route = spcquiz(
          "q1",
          Behaviors.receiveMessage {
            case Quiz.Update("q1 title+", "q1 intro+", 55, `p1`, replyTo) =>
              replyTo ! Resp.OK
              Behaviors.stopped
            case x =>
              fail(s"received wrong command $x")
              Behaviors.stopped
          }
        )
        put("quiz/q1", p1.id, updt) ~> route ~>
          check {
            status shouldBe StatusCodes.NoContent
          }
      }
      "not update quiz with error" in {
        val err = Quiz.quizNotFound.error() + "q1"
        put("quiz/q1", p1.id, updt) ~> stdquiz("q1", Bad(err)) ~>
          check {
            status shouldBe StatusCodes.UnprocessableEntity
            responseAs[Error] shouldBe err
          }
      }
    }
    "POST" should {
      val addsec = CreateSection("section title")
      "add section" in {
        val route = spcquiz(
          "q1",
          Behaviors.receiveMessage {
            case Quiz.AddSection("section title", `p2`, replyTo) =>
              replyTo ! Good("1")
              Behaviors.stopped
            case x =>
              fail(s"received wrong command $x")
              Behaviors.stopped
          }
        )
        post("quiz/q1", p2.id, addsec) ~> route ~>
          check {
            status shouldBe StatusCodes.OK
            responseAs[String] shouldBe "1"
          }
      }
      "not add section with error" in {
        val err = Quiz.tooShortTitle.error()
        post("quiz/q1", p2.id, addsec) ~> stdquiz("q1", Bad(err)) ~>
          check {
            status shouldBe StatusCodes.UnprocessableEntity
            responseAs[Error] shouldBe err
          }
      }
    }
    "HEAD" should {
      "own section" in {
        val route = spcquiz(
          "qx",
          Behaviors.receiveMessage {
            case Quiz.OwnSection("qx-1", `p3`, replyTo) =>
              replyTo ! Resp.OK
              Behaviors.stopped
            case x =>
              fail(s"received wrong command $x")
              Behaviors.stopped
          }
        )
        head("quiz/qx?sc=qx-1", p3.id) ~> route ~>
          check {
            status shouldBe StatusCodes.NoContent
          }
      }
      "not own section" in {
        val err = Quiz.sectionNotFound.error() + "qx-1"
        head("quiz/qx?sc=qx-1", p3.id) ~> stdquiz("qx", Bad(err)) ~>
          check {
            status shouldBe StatusCodes.UnprocessableEntity
            responseAs[Error] shouldBe err
          }
      }
    }
    "DELETE" should {
      "set obsolete" in {
        val route = spcquiz(
          "qx",
          Behaviors.receiveMessage {
            case Quiz.SetObsolete(`p3`, replyTo) =>
              replyTo ! Resp.OK
              Behaviors.stopped
            case x =>
              fail(s"received wrong command $x")
              Behaviors.stopped
          }
        )
        delete("quiz/qx", p3.id) ~> route ~>
          check {
            status shouldBe StatusCodes.NoContent
          }
      }
      "not set obsolete with error" in {
        val err = Quiz.quizNotFound.error()
        delete("quiz/qx", p3.id) ~> stdquiz("qx", Bad(err)) ~>
          check {
            status shouldBe StatusCodes.UnprocessableEntity
            responseAs[Error] shouldBe err
          }
      }
    }
  }

  "quiz/{id}/ready" when {
    "DELETE" should {
      "unset readiness sign" in {
        val route = spcquiz(
          "qx",
          Behaviors.receiveMessage {
            case Quiz.UnsetReadySign(`p4`, replyTo) =>
              replyTo ! Resp.OK
              Behaviors.stopped
            case x =>
              fail(s"received wrong command $x")
              Behaviors.stopped
          }
        )
        delete("quiz/qx/ready", p4.id) ~> route ~>
          check {
            status shouldBe StatusCodes.NoContent
          }
      }
      "not unset readiness sign with error" in {
        val err = Quiz.notAuthor.error()
        delete("quiz/qx/ready", p4.id) ~> stdquiz("qx", Bad(err)) ~>
          check {
            status shouldBe StatusCodes.UnprocessableEntity
            responseAs[Error] shouldBe err
          }
      }
    }
    "HEAD" should {
      "set readiness sign" in {
        val route = spcquiz(
          "qx",
          Behaviors.receiveMessage {
            case Quiz.SetReadySign(`p4`, replyTo) =>
              replyTo ! Resp.OK
              Behaviors.stopped
            case x =>
              fail(s"received wrong command $x")
              Behaviors.stopped
          }
        )
        head("quiz/qx/ready", p4.id) ~> route ~>
          check {
            status shouldBe StatusCodes.NoContent
          }
      }
      "not set readiness sign with error" in {
        val err = Quiz.notAuthor.error()
        head("quiz/qx/ready", p4.id) ~> stdquiz("qx", Bad(err)) ~>
          check {
            status shouldBe StatusCodes.UnprocessableEntity
            responseAs[Error] shouldBe err
          }
      }
    }
  }

  "quiz/{id}/resolve" when {
    "HEAD" should {
      "approve" in {
        val route = spcquiz(
          "qx",
          Behaviors.receiveMessage {
            case Quiz.Resolve(`p4`, true, replyTo) =>
              replyTo ! Resp.OK
              Behaviors.stopped
            case x =>
              fail(s"received wrong command $x")
              Behaviors.stopped
          }
        )
        delete("quiz/qx/resolve", p4.id) ~> route ~>
          check {
            status shouldBe StatusCodes.NoContent
          }
      }
      "not approve with error" in {
        val err = Quiz.notAuthor.error()
        delete("quiz/qx/resolve", p4.id) ~> stdquiz("qx", Bad(err)) ~>
          check {
            status shouldBe StatusCodes.UnprocessableEntity
            responseAs[Error] shouldBe err
          }
      }
    }
    "DELETE" should {
      "disapprove" in {
        val route = spcquiz(
          "qx",
          Behaviors.receiveMessage {
            case Quiz.Resolve(`p4`, false, replyTo) =>
              replyTo ! Resp.OK
              Behaviors.stopped
            case x =>
              fail(s"received wrong command $x")
              Behaviors.stopped
          }
        )
        head("quiz/qx/resolve", p4.id) ~> route ~>
          check {
            status shouldBe StatusCodes.NoContent
          }
      }
      "not disapprove with error" in {
        val err = Quiz.notAuthor.error()
        head("quiz/qx/resolve", p4.id) ~> stdquiz("qx", Bad(err)) ~>
          check {
            status shouldBe StatusCodes.UnprocessableEntity
            responseAs[Error] shouldBe err
          }
      }
    }
  }

  "quiz/{id}/authors" when {
    "HEAD" should {
      "add author" in {
        val route = spcquiz(
          "qx",
          Behaviors.receiveMessage {
            case Quiz.AddAuthor(`p1`, `p2`, replyTo) =>
              replyTo ! Resp.OK
              Behaviors.stopped
            case x =>
              fail(s"received wrong command $x")
              Behaviors.stopped
          }
        )
        head("quiz/qx/authors", p1.id) ~> route ~>
          check {
            status shouldBe StatusCodes.NoContent
          }
      }
      "not add author with error" in {
        val err = Quiz.quizNotFound.error()
        head("quiz/qx/authors", p1.id) ~> stdquiz("qx", Bad(err)) ~>
          check {
            status shouldBe StatusCodes.UnprocessableEntity
            responseAs[Error] shouldBe err
          }
      }
    }
    "DELETE" should {
      "remove author" in {
        val route = spcquiz(
          "qx",
          Behaviors.receiveMessage {
            case Quiz.RemoveAuthor(`p1`, `p2`, replyTo) =>
              replyTo ! Resp.OK
              Behaviors.stopped
            case x =>
              fail(s"received wrong command $x")
              Behaviors.stopped
          }
        )
        delete("quiz/qx/authors", p1.id) ~> route ~>
          check {
            status shouldBe StatusCodes.NoContent
          }
      }
      "not remove author with error" in {
        val err = Quiz.quizNotFound.error()
        delete("quiz/qx/authors", p1.id) ~> stdquiz("qx", Bad(err)) ~>
          check {
            status shouldBe StatusCodes.UnprocessableEntity
            responseAs[Error] shouldBe err
          }
      }
    }
  }

  "quiz/{id}/inspectors" when {
    "HEAD" should {
      "add inspector" in {
        val route = spcquiz(
          "qx",
          Behaviors.receiveMessage {
            case Quiz.AddInspector(`p1`, `p2`, replyTo) =>
              replyTo ! Resp.OK
              Behaviors.stopped
            case x =>
              fail(s"received wrong command $x")
              Behaviors.stopped
          }
        )
        head("quiz/qx/inspectors", p1.id) ~> route ~>
          check {
            status shouldBe StatusCodes.NoContent
          }
      }
      "not add inspector with error" in {
        val err = Quiz.quizNotFound.error()
        head("quiz/qx/inspectors", p1.id) ~> stdquiz("qx", Bad(err)) ~>
          check {
            status shouldBe StatusCodes.UnprocessableEntity
            responseAs[Error] shouldBe err
          }
      }
    }
    "DELETE" should {
      "remove inspector" in {
        val route = spcquiz(
          "qx",
          Behaviors.receiveMessage {
            case Quiz.RemoveInspector(`p1`, `p2`, replyTo) =>
              replyTo ! Resp.OK
              Behaviors.stopped
            case x =>
              fail(s"received wrong command $x")
              Behaviors.stopped
          }
        )
        delete("quiz/qx/inspectors", p1.id) ~> route ~>
          check {
            status shouldBe StatusCodes.NoContent
          }
      }
      "not remove inspector with error" in {
        val err = Quiz.quizNotFound.error()
        delete("quiz/qx/inspectors", p1.id) ~> stdquiz("qx", Bad(err)) ~>
          check {
            status shouldBe StatusCodes.UnprocessableEntity
            responseAs[Error] shouldBe err
          }
      }
    }
  }

  "section/{id}" when {
    "PUT" should {
      "update section" in {
        val route = spcsect(
          "sx",
          Behaviors.receiveMessage {
            case SectionEdit.Update(`p2`, "new title", "new intro", replyTo) =>
              replyTo ! Resp.OK
              Behaviors.stopped
            case x =>
              fail(s"received wrong command $x")
              Behaviors.stopped
          }
        )
        put("section/sx", p2.id, UpdateSection("new title", "new intro")) ~> route ~>
          check {
            status shouldBe StatusCodes.NoContent
          }
      }
      "not update section with error" in {
        val err = SectionEdit.notOwned.error()
        put("section/sx", p2.id, UpdateSection("", "")) ~> stdsect("sx", Bad(err)) ~>
          check {
            status shouldBe StatusCodes.UnprocessableEntity
            responseAs[Error] shouldBe err
          }
      }
    }
    "PATCH" should {
      def checkMove(up: Boolean) =
        val route = spcquiz(
          "qx",
          Behaviors.receiveMessage {
            case Quiz.MoveSection("sx", `up`, `p3`, replyTo) =>
              replyTo ! Good(List("s1", "sx", "s2"))
              Behaviors.stopped
            case x =>
              fail(s"received wrong command $x")
              Behaviors.stopped
          }
        )
        patch(s"section/sx?up=$up&qid=qx", p3.id) ~> route ->
          check {
            status shouldBe StatusCodes.OK
            responseAs[List[String]] shouldBe List("s1", "sx", "s2")
          }
      "move section up" in {
        checkMove(true)
      }
      "move section down" in {
        checkMove(false)
      }
      "not move section with error" in {
        val err = Quiz.sectionNotFound.error()
        patch("section/sx?up=true&qid=qx", p3.id) ~> stdquiz("qx", Bad(err)) ~>
          check {
            status shouldBe StatusCodes.UnprocessableEntity
            responseAs[Error] shouldBe err
          }
      }
    }
    "DELETE" should {
      "remove section" in {
        val route = spcquiz(
          "qx",
          Behaviors.receiveMessage {
            case Quiz.RemoveSection("sx", `p4`, replyTo) =>
              replyTo ! Resp.OK
              Behaviors.stopped
            case x =>
              fail(s"received wrong command $x")
              Behaviors.stopped
          }
        )
        delete("section/sx?qid=qx", p4.id) ~> route ~>
          check {
            status shouldBe StatusCodes.NoContent
          }
      }
      "not remove section with error" in {
        val err = Quiz.sectionNotFound.error()
        delete("section/sx?qid=qx", p4.id) ~> stdquiz("qx", Bad(err)) ~>
          check {
            status shouldBe StatusCodes.UnprocessableEntity
            responseAs[Error] shouldBe err
          }
      }
    }
    "HEAD" should {
      "discharge section" in {
        val route = spcsect(
          "sx",
          Behaviors.receiveMessage {
            case SectionEdit.Discharge(`p3`, replyTo) =>
              replyTo ! Resp.OK
              Behaviors.stopped
            case x =>
              fail(s"received wrong command $x")
              Behaviors.stopped
          }
        )
        head("section/sx", p3.id) ~> route ~>
          check {
            status shouldBe StatusCodes.NoContent
          }
      }
      "not discharge section with error" in {
        val err = SectionEdit.notOwned.error()
        head("section/sx", p3.id) ~> stdsect("sx", Bad(err)) ~>
          check {
            status shouldBe StatusCodes.UnprocessableEntity
            responseAs[Error] shouldBe err
          }
      }
    }
  }

  "section/{id}/items" when {
    "PUT" should {
      val item = Item("1", "", Statement("", None), List.empty, false, List.empty)
      "save item" in {
        val route = spcsect(
          "sx",
          Behaviors.receiveMessage {
            case SectionEdit.SaveItem(`p2`, `item`, replyTo) =>
              replyTo ! Resp.OK
              Behaviors.stopped
            case x =>
              fail(s"received wrong command $x")
              Behaviors.stopped
          }
        )
        put("section/sx/items", p2.id, item) ~> route ~>
          check {
            status shouldBe StatusCodes.NoContent
          }
      }
      "not save item with error" in {
        val err = SectionEdit.itemNotFound.error()
        put("section/sx/items", p2.id, item) ~> stdsect("sx", Bad(err)) ~>
          check {
            status shouldBe StatusCodes.UnprocessableEntity
            responseAs[Error] shouldBe err
          }
      }
    }
    "PATCH" should {
      "add item" in {
        val route = spcsect(
          "sx",
          Behaviors.receiveMessage {
            case SectionEdit.AddItem(`p3`, replyTo) =>
              replyTo ! Good("1")
              Behaviors.stopped
            case x =>
              fail(s"received wrong command $x")
              Behaviors.stopped
          }
        )
        patch("section/sx/items", p3.id) ~> route ~>
          check {
            status shouldBe StatusCodes.NoContent
          }
      }
      "not add item with error" in {
        val err = SectionEdit.notOwned.error()
        patch("section/sx/items", p3.id) ~> stdsect("sx", Bad(err)) ~>
          check {
            status shouldBe StatusCodes.UnprocessableEntity
            responseAs[Error] shouldBe err
          }
      }
    }
  }

  "section/{id}/items/{itemId}" when {
    "PATCH" should {
      def moveItem(up: Boolean) =
        val route = spcsect(
          "sx",
          Behaviors.receiveMessage {
            case SectionEdit.MoveItem(`p4`, "1", `up`, replyTo) =>
              replyTo ! Good(List("1", "2", "3"))
              Behaviors.stopped
            case x =>
              fail(s"received wrong command $x")
              Behaviors.stopped
          }
        )
        patch("section/sx/items/1", p4.id) ~> route ~>
          check {
            status shouldBe StatusCodes.OK
            responseAs[List[String]] shouldBe List("1", "2", "3")
          }
      "move item up" in {
        moveItem(true)
      }
      "move item down" in {
        moveItem(false)
      }
      "not move item with error" in {
        val err = SectionEdit.itemNotFound.error()
        patch("section/sx/items/1", p4.id) ~> stdsect("sx", Bad(err)) ~>
          check {
            status shouldBe StatusCodes.UnprocessableEntity
            responseAs[Error] shouldBe err
          }
      }
    }
    "DELETE" should {
      "remove item" in {
        val route = spcsect(
          "sx",
          Behaviors.receiveMessage {
            case SectionEdit.RemoveItem(`p2`, "3", replyTo) =>
              replyTo ! Resp.OK
              Behaviors.stopped
            case x =>
              fail(s"received wrong command $x")
              Behaviors.stopped
          }
        )
        delete("section/sx/items/3", p2.id) ~> route ~>
          check {
            status shouldBe StatusCodes.NoContent
          }
      }
      "not remove item with error" in {
        val err = SectionEdit.itemNotFound.error()
        delete("section/sx/items/3", p2.id) ~> stdsect("sx", Bad(err)) ~>
          check {
            status shouldBe StatusCodes.UnprocessableEntity
            responseAs[Error] shouldBe err
          }
      }
    }
  }
