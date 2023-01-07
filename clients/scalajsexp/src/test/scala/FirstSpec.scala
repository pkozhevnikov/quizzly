package school

import org.scalatest.*

import org.scalajs.dom.*
import html.*

class FirstSpec extends wordspec.AsyncWordSpec, BeforeAndAfterAll, matchers.should.Matchers:

  setupUI

  "button click" should {
    "be present" in {
      document.querySelectorAll("button").count(_.textContent == "Click me") shouldBe 1
    }

    "react on click" in {
      val button = document.querySelector("button").asInstanceOf[Button]
      button.textContent shouldBe "Click me"
      def msgcount = document.querySelectorAll("p").count(_.textContent == "Button clicked Click me")
      for c <- 1 to 3 do
        button.click()
        msgcount shouldBe c
      1 shouldBe 1
    }
  }
