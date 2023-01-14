import { Component, OnInit } from '@angular/core'
import { FormControl } from "@angular/forms"
import { Person, PersonsState } from "../../persons.state"
import { QuizzesQuery } from "../../quizzes/state/quizzes.query"
import { ExamsService } from "../state/exams.service"
import { Quiz, createQuiz } from "../../quizzes/state/quiz.model"
import { ActivatedRoute } from "@angular/router"
import * as dayjs from "dayjs"
import { startWith, switchMap, tap, map } from "rxjs"

@Component({
  selector: 'app-newexam',
  templateUrl: './newexam.component.html',
  styleUrls: ['./newexam.component.css']
})
export class NewexamComponent implements OnInit {

  personsToSelect: Person[] = []
  selectedPersons: Person[] = []
  period: {start: dayjs.Dayjs, end: dayjs.Dayjs} = {start: dayjs(), end: dayjs()}
  quiz: Quiz = createQuiz({})
  examId = new FormControl("")
  trialLength = new FormControl("")
  personFilter = new FormControl("")
  persons$ = this.personFilter.valueChanges
    .pipe(startWith(""))
    .pipe(map(v => (typeof v === "string") ? v : ""))
    .pipe(switchMap(v => this.personsState.selectAll(v)))

  constructor(
    private quizzesQuery: QuizzesQuery,
    private examsService: ExamsService,
    private personsState: PersonsState,
    private route: ActivatedRoute
  ) { }

  ngOnInit(): void {
    this.route.params.subscribe(params => {
      const q = this.quizzesQuery.getEntity(params["quizId"])
      if (q)
        this.quiz = q
    })
  }

  removeTestee(id: string) {
    this.selectedPersons = this.selectedPersons.filter(p => p.id != id)
  }

  addTestees() {
    this.selectedPersons = [...this.personsToSelect]
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
