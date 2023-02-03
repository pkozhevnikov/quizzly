package quizzly.school

import org.scalatest.*

class DefaultGraderSpec extends wordspec.AnyWordSpec, matchers.should.Matchers, BeforeAndAfterEach:


  def oneItem(hints: Int, hintsVisible: Boolean, solutions: List[Int]) =
    List(Section("s1", "", "", List(Item("s1i1", "", Statement("", None),
      List.fill(hints)(List.empty), hintsVisible, solutions))))

  var grader = DefaultGrader()

  "DefaultGraderSpec" should {

    "estimate correct sinlge choice item" in {
      def sol(value: String) = List(Solution("s1", "s1i1", List(value)))
      val sections = oneItem(hints = 5, hintsVisible = true, solutions = List(3))
      grader.estimate(0, sections, 0, sol("3")) shouldBe 100
      grader.estimate(0, sections, 0, sol("2")) shouldBe 0
      grader.estimate(0, sections, 0, List.empty) shouldBe 0
    }

    "estimate trial correctly" ignore {
      val sections = List(
        Section("s1", "", "", List(
          Item(
            "s1i1",
            "",
            Statement("", None),
            List.fill(5)(List.empty),
            true,
            List(0, 2)
        )))
      )
      val grader = DefaultGrader()
      //grader.estimate(0, sections, 0, List("1", "2")) shouldBe 50
      //grader.estimate(0, sections, 0, List("0", "2")) shouldBe 100
      //grader.estimate(0, sections, 0, List("0", "1", "2", "3", "4")) shouldBe 
    }

  }
