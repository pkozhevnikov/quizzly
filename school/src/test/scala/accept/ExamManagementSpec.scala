package quizzly.school.accept

import org.scalatest.*


class ExamManagementSpec extends featurespec.AnyFeatureSpec, GivenWhenThen:

  info("As a Staff user")
  info("I want to create, modify and cancel an Exam")

  Feature("Create an Exam") {

    Scenario("Exam is created if all input is correct") {
      Given("specified unique Exam identifier")
      And("specified Quiz")
      And("specified Exam Period")
      When("create request is performed")
      Then("new Exam is created")
      And("its state is Pending")
      And("I am a Host of this Exam")
      fail()
    }

    Scenario("Exam is not created if identifier is not unique") {
      Given("specified identifier that already exists")
      And("specified Quiz")
      And("specified Exam Period")
      When("create request is performed")
      Then("new Exam is not created")
      And("response contains detailed reason")
    }
  }
      
  Feature("Include/exclude a Testee") {

    Scenario("Testee added") {
      Given("existing Exam")
      And("it is in Pending state")
      And("I am a Host")
      And("specified a user as a new Testee")
      When("'add testee' request is performed")
      Then("specified user is on Exam list as a Testee")
    }

    Scenario("Testee removed") {
      Given("existing Exam")
      And("it is in Pending state")
      And("I am a Host")
      And("a specific Testee is on list")
      When("'remove testee' request is performed")
      Then("specified Testee is no longer on Exam list")
    }
  }

  Feature("Modify Trial Length") {
    Scenario("Trial Length is changed") {
      Given("existing Exam")
      And("it is in Pending state")
      And("I am a Host")
      And("specified new Trial Length")
      When("'change trial length' request is performed")
      Then("specified Trial Length is set on the Exam")
    }
  }

  Feature("Cancel Exam") {
    Scenario("Exam is cancelled") {
      Given("existing Exam")
      And("it is in Pending or Upcoming state")
      And("I am a Host")
      When("'cancel exam' request is performed")
      Then("the exam is in Cancelled state")
    }
  }
