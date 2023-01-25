package quizzly.trial

import akka.actor.typed.{Behavior, ActorRef}
import akka.cluster.sharding.typed.scaladsl.EntityTypeKey
import akka.persistence.typed.PersistenceId
import akka.persistence.typed.scaladsl.*
import akka.Done
import akka.pattern.StatusReply

object TrialEntity:

  import Trial.*

  val EntityKey: EntityTypeKey[Command] = EntityTypeKey("Trial")

  object Tags:
    val Single = "trial"
    val All = Vector("trial-1", "trial-2")

  def apply(id: TrialID) = EventSourcedBehavior[Command, Event, Option[Trial]](
    PersistenceId.of(EntityKey.name, id),
    None,
    (state, command) =>
      state match
        case None =>
          Effect.unhandled
        case Some(trial) =>
          takeCommand(trial, command)
    ,
    (state, event) =>
      state match
        case None =>
          event match
            case Started(testee, exam, quiz, length, at) =>
              Some(Trial(testee, exam, quiz, length, at, None, Map.empty))
            case _ =>
              throw IllegalStateException("trial not started yet")
        case Some(trial) =>
          takeEvent(trial, event)
  ).withTagger(_ => Set(Tags.Single))

  def takeCommand(state: Trial, command: Command): Effect[Event, Option[Trial]] = ???

  def takeEvent(state: Trial, event: Event): Option[Trial] = ???
