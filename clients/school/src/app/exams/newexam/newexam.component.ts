import { Component, OnInit } from "@angular/core"
import { FormControl } from "@angular/forms"
import { Person } from "../../persons.state"
import { QuizzesQuery } from "../../quizzes/state/quizzes.query"
import { ExamsService } from "../state/exams.service"
import { Quiz, createQuiz } from "../../quizzes/state/quiz.model"
import { ActivatedRoute } from "@angular/router"
import * as dayjs from "dayjs"

@Component({
  selector: "app-newexam",
  templateUrl: "./newexam.component.html",
  styleUrls: ["./newexam.component.css"]
})
export class NewexamComponent implements OnInit {

  selectedPersons: Person[] = []
  period: {start: dayjs.Dayjs, end: dayjs.Dayjs} = {start: dayjs(), end: dayjs()}
  quiz: Quiz = createQuiz({})
  examId = new FormControl("")
  trialLength = new FormControl("")

  constructor(
    private quizzesQuery: QuizzesQuery,
    private examsService: ExamsService,
    private route: ActivatedRoute
  ) { }

  ngOnInit(): void {
    this.route.params.subscribe(params => {
      const q = this.quizzesQuery.getEntity(params["quizId"])
      if (q)
        this.quiz = q
    })
  }

  personsSelected(persons: Person[]) {
    this.selectedPersons = persons
  }

  createExam() {
    this.examsService.create({
      id: this.examId.value!,
      quizId: this.quiz.id,
      start: this.period.start.toDate(),
      end: this.period.end.toDate(),
      trialLength: Number(this.trialLength.value),
      testees: this.selectedPersons.map(p => p.id)
    })
  }

}
