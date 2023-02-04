package quizzly.school

import org.scalatest.*

class DefaultGraderSpec extends wordspec.AnyWordSpec, matchers.should.Matchers, BeforeAndAfterEach:

  def oneItem(
      hints: Int,
      hintsVisible: Boolean,
      solutions: List[Int],
      definitionText: String = ""
  ) = List(
    Section(
      "s1",
      "",
      "",
      List(
        Item(
          "s1i1",
          "",
          Statement(definitionText, None),
          List.fill(hints)(List.empty),
          hintsVisible,
          solutions
        )
      )
    )
  )

  var grader = DefaultGrader()

  val addword = afterWord("estimate")

  "DefaultGrader" should addword {

    "item" which {

      "single choice" in {
        def sol(value: String) = List(Solution("s1", "s1i1", List(value)))
        val sections = oneItem(hints = 5, hintsVisible = true, solutions = List(3))
        grader.estimate(0, sections, 0, sol("3")) shouldBe 100
        grader.estimate(0, sections, 0, sol("2")) shouldBe 0
        grader.estimate(0, sections, 0, List.empty) shouldBe 0
      }

      "multi choice" in {
        val sections = oneItem(hints = 9, hintsVisible = true, solutions = List(1, 4, 7))
        def estimate(value: Int*) = grader
          .estimate(0, sections, 0, List(Solution("s1", "s1i1", value.toList.map(_.toString))))
        estimate(1, 4, 7) shouldBe 100
        estimate(1, 4, 3) shouldBe 67
        estimate(1, 4) shouldBe 0
        estimate(1, 2, 4, 7) shouldBe 0
        estimate(1, 5, 7) shouldBe 67
        estimate(0, 1, 5) shouldBe 34
        estimate(2, 3, 7) shouldBe 34
      }

      "fill select" in {
        val sections = oneItem(
          hints = 9,
          hintsVisible = true,
          solutions = List.empty,
          "text {{3}} text {{7}} text {{6}}"
        )
        def estimate(value: Int*) = grader
          .estimate(0, sections, 0, List(Solution("s1", "s1i1", value.toList.map(_.toString))))
        estimate(2, 6, 5) shouldBe 100
        estimate(2, 6, 5, 0) shouldBe 0
        estimate(2, 3, 5) shouldBe 67
        estimate(6, 5, 7) shouldBe 0
        estimate(2, 6, 7) shouldBe 67
        estimate(1, 6, 3) shouldBe 34
        estimate(1, 6, 5) shouldBe 67
      }

      "fill by hand" in {
        val sections = List(
          Section(
            "s1",
            "",
            "",
            List(
              Item(
                "s1i1",
                "",
                Statement("text {{1}} text {{5}} text {{6}}", None),
                (1 to 8)
                  .map(n =>
                    List(Statement(s"hint$n ALT1", None), Statement(s"hint$n alt2", None))
                  )
                  .toList,
                false,
                List.empty
              )
            )
          )
        )

        def estimate(value: String*) = 
          grader.estimate(0, sections, 0, List(Solution("s1", "s1i1", value.toList)))

        estimate("hint1 aLT1", "HINT5 alt2", "HINT6 ALT1") shouldBe 100
        estimate("hint1 aLT1", "HINT5 alt2", "HINT6 ALT1", "x") shouldBe 0
        estimate("", "", "") shouldBe 0
        estimate("x", "hint1 alt1", "hint6 alt2") shouldBe 34
        estimate("hint1 alt2", "y", "hint6 alt1") shouldBe 67
      }

    }

    "one section" which {

      "contains mutiple items" in {
        //format off
        val sections = List(
          Section(
            "s1", "", "", List(
              Item("s1i1", "", Statement("", None), List.fill(5)(List.empty), true, List(1,3)),
              Item("s1i2", "", Statement("", None), List.fill(3)(List.empty), true, List(2)),
              Item("s1i3", "", Statement("{{1}} {{3}}", None), List.fill(5)(List.empty), true, List.empty),
              Item("s1i4", "", Statement("{{1}} {{3}}", None), List(
                List(Statement("hint1", None)), List(Statement("hint2", None)),
                List(Statement("hint3", None)), List(Statement("hint4", None))
              ), false, List.empty)
            )
          )
        )
        grader.estimate(0, sections, 0, List(
          Solution("s1", "s1i2", List("2")),
          Solution("s1", "s1i1", List("1", "3")),
          Solution("s1", "s1i3", List("0", "2")),
          Solution("s1", "s1i4", List("hint1", "hint3"))
        )) shouldBe 100
        grader.estimate(0, sections, 0, List(
          Solution("s1", "s1i1", List.empty),
          Solution("s1", "s1i2", List("2")),
          Solution("s1", "s1i3", List("0", "3")),
          Solution("s1", "s1i4", List("Hint2", "hint3"))
        )) shouldBe 50
        //format on
      }

    }

    "multiple sections" that {
      "contains one item each" in {
        //format off
        val sections = List(
          Section(
            "s1", "", "", List(
              Item("s1i1", "", Statement("", None), List.fill(6)(List.empty), true, List(1, 3, 4))
            )
          ),
          Section(
            "s2", "", "", List(
              Item("s2i1", "", Statement("", None), List.fill(5)(List.empty), true, List(2))
            )
          )
        )
        grader.estimate(0, sections, 0, List(
          Solution("s1", "s1i1", List("1", "2", "3")),
          Solution("s2", "s2i1", List("2"))
        )) shouldBe 84
        grader.estimate(0, sections, 0, List(
          Solution("s1", "s1i1", List("1", "2", "5"))
        )) shouldBe 17
        //fomat on
      }
    }

  }
