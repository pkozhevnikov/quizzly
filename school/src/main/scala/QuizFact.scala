package quizzly.school

import akka.actor.typed.{Behavior, ActorRef}
import akka.cluster.sharding.typed.scaladsl.EntityTypeKey
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.*

object QuizFact:

  sealed trait Command extends CborSerializable
  sealed trait Event extends CborSerializable

  sealed trait State extends CborSerializable
  final case class Blank(id: QuizID) extends State
  final case class Fact(
      id: QuizID,
      title: String,
      obsolete: Boolean,
      everPublished: Boolean,
      isPublished: Boolean,
      usedBy: Set[ExamID]
  ) extends State

  val EntityKey: EntityTypeKey[Command] = EntityTypeKey("QuizFact")

  def apply(id: QuizID, config: ExamConfig): Behavior[Command] =
    EventSourcedBehavior[Command, Event, State](
      PersistenceId.ofUniqueId(id),
      Blank(id),
      commandHandler(config),
      eventHandler
    )

  def commandHandler(config: ExamConfig): (State, Command) => Effect[Event, State] =
    (state, cmd) =>
      state match

        case _: Blank =>
          Effect.unhandled

        case fact: Fact =>
          Effect.none

  val eventHandler: (State, Event) => State =
    (state, evt) =>
      state match

        case _: Blank =>
          state

        case fact: Fact =>
          state
