package quizzly.school

import util.Try

trait Grader:
  def estimate(
      trialLengthMinutes: Int,
      sections: List[Section],
      trialDurationSeconds: Int,
      solutions: List[Solution]
  ): Int

class DefaultGrader extends Grader:

  def estimate(
      trialLengthMinutes: Int,
      sections: List[Section],
      trialDurationSeconds: Int,
      solutions: List[Solution]
  ) = 
    val allItems = sections.flatMap(section => section.items.map(item => {
      solutions.find(sol => sol.sectionSc == section.sc && sol.itemSc == item.sc) match
        case Some(solution) =>
          gradeItem(item, solution.answers)
        case None =>
          0
    }))
    math.ceil(allItems.sum.toDouble / allItems.length).toInt

  val phr = "\\{\\{(\\d+)}}".r

  def gradeItem(item: Item, answers: List[String]): Int =
    val fillSolutions = phr.findAllMatchIn(item.definition.text).map(_.group(1)).toList
    val (sol, ans) = 
    if fillSolutions.isEmpty then
      (item.solutions, answers.map(v => Try(v.toInt)).filter(_.isSuccess).map(_.get))
    else
      (fillSolutions.map(_.toInt), List.empty)

    if ans.length != sol.length then
      0
    else
      math.ceil(sol.zip(ans).count((s, a) => s == a).toDouble / sol.length * 100).toInt
