import { Component, OnInit } from '@angular/core'

import { QuizzesQuery } from "../state/quizzes.query"
import { QuizzesService } from "../state/quizzes.service"
import { Observable, empty } from "rxjs"
import { Quiz } from "../state/quiz.model"

@Component({
  selector: 'app-quizlist',
  templateUrl: './quizlist.component.html',
  styleUrls: ['./quizlist.component.css']
})
export class QuizlistComponent implements OnInit {

  constructor(
    private quizzesService: QuizzesService,
    public quizzesQuery: QuizzesQuery
  ) { }

  ngOnInit(): void {
    this.quizzesService.get()
  }

  list$: Observable<Quiz[]> = this.quizzesQuery.selectAll()

  publish(id: string) {
    this.quizzesService.publish(id)
  }

  unpublish(id: string) {
    this.quizzesService.unpublish(id)
  }

}
