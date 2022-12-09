package quizzly.author

import org.scalatest.*
import akka.actor.testkit.typed.scaladsl.ActorTestKit
import akka.projection.testkit.scaladsl.ProjectionTestKit

class SlickLocalProjectionHandlerSpec extends wordspec.AnyWordSpec, matchers.should.Matchers:

  // val projTestKit = ProjectionTestKit(system)

  def changeDb = afterWord("change db on event:")

  "LocalProjection" should
    changeDb {

      "quiz creation" in {}

      "quiz update" in {}

      "add author" in {}

      "remove author" in {}

      "add inspector" in {}

      "remove inspector" in {}

      "change state to review" in {}

      "change state to compsing" in {}

      "change state to released" in {}

      "set obsolete" in {}

    }
