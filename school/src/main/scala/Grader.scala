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
    val allItems = sections.flatMap(section =>
      section
        .items
        .map(item => {
          solutions.find(sol => sol.sectionSc == section.sc && sol.itemSc == item.sc) match
            case Some(solution) =>
              gradeItem(item, solution.answers)
            case None =>
              0
        })
    )
    math.ceil(allItems.sum / allItems.length).toInt

  val phr = "\\{\\{(\\d+)}}".r

  def gradeItem(item: Item, answers: List[String]): Double =
    val fillSolutions =
      phr.findAllMatchIn(item.definition.text).map(_.group(1).toInt - 1).toList.distinct
    def intAnswers =
      answers
        .map(v => Try(v.toInt))
        .filter(_.isSuccess)
        .map(_.get)
        .filter(idx => idx > -1 && idx < item.hints.length)
        .distinct
    if fillSolutions.isEmpty then
      val ans = intAnswers
      if ans.length != item.solutions.length then
        0
      else
        ans.count(item.solutions.contains(_)).toDouble / item.solutions.length * 100
    else if item.hintsVisible then
      val ans = intAnswers
      if ans.length != fillSolutions.length then
        0
      else
        fillSolutions.zip(ans).count((s, a) => s == a).toDouble / fillSolutions.length * 100
    else if answers.length != fillSolutions.length then
      0
    else
      fillSolutions
        .zip(answers)
        .count { (s, a) =>
          item.hints(s).map(_.text.toLowerCase.trim).contains(a.toLowerCase.trim)
        }
        .toDouble / fillSolutions.length * 100
