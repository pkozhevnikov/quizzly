package quizzly.school

import akka.actor.typed.{Behavior, ActorRef}
import akka.cluster.sharding.typed.scaladsl.EntityTypeKey
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.*

object ExamEntity:

  import Exam.*

  val EntityKey: EntityTypeKey[Command] = EntityTypeKey("Exam")

  def apply(id: ExamID, config: ExamConfig): Behavior[Command] =
    EventSourcedBehavior[Command, Event, Exam](
      PersistenceId.ofUniqueId(id),
      Blank(id),
      commandHandler(config),
      eventHandler
    )

  def commandHandler(config: ExamConfig): (Exam, Command) => Effect[Event, Exam] =
    (state, cmd) =>
      state match

        case _: Blank =>
          Effect.unhandled

        case pending: Pending =>
          Effect.none

        case upcoming: Upcoming =>
          Effect.none

        case inprogress: InProgress =>
          Effect.none

        case ended: Ended =>
          Effect.none

        case cancelled: Cancelled =>
          Effect.none

  val eventHandler: (Exam, Event) => Exam =
    (state, evt) =>
      state match

        case _: Blank =>
          state

        case pending: Pending =>
          state

        case upcoming: Upcoming =>
          state

        case inprogress: InProgress =>
          state

        case ended: Ended =>
          state

        case cancelled: Cancelled =>
          state
