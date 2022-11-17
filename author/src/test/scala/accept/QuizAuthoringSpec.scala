package quizzly.author.accept

import org.scalatest.*

class QuizAuthoringSpec extends featurespec.AnyFeatureSpec, GivenWhenThen:

  info("As an Official")
  info("I want to manage a Quiz")

  Feature("Quiz creation") {

    Scenario("Quiz created") {
      Given("unique identifier specified")
      And("title and intro specified")
      And("Authors and Inspectors specified")
      And("Testees specified")
      When("'create quiz' request is sent")
      Then("new Quiz created")
      And("I am a Curator")
      And("new Quiz is in Composing state")
      And("Quiz section are not modifiable")
    }

    Scenario("Quiz not created 1") {
      Given("not unique identifier specified")
      And("title and intro specified")
      And("Authors and Inspectors specified")
      And("Testees specified")
      When("'create quiz' request is sent")
      Then("Quiz is not created")
      And("'quiz already exists' message is displayed")
    }

    Scenario("Quiz not created 2") {
      Given("unique identifier specified")
      And("title and intro specified")
      And("Authors and Inspectors not specified")
      And("Testees not specified")
      When("'create quiz' request is sent")
      Then("Quiz is not created")
      And("'not enough authors or inspectors' message is displayed")
    }

    Scenario("main Quiz attributes changed") {
      Given("a Quiz in Composing state")
      And("I am a Curator")
      And("modified title, intro, recommended length")
      When("'save' request is performed")
      Then("new title, recommended length and intro saved")
    }

    Scenario("set a Quiz Obsolete")(pending)

  }

  info("")

  info("As an Author")
  info("I want to modify a Quiz")

  Feature("Quiz modification")(pending)

  info("")

  info("As an Inspector")
  info("I want to assess a Quiz")

  Feature("Quiz inspection")(pending)
