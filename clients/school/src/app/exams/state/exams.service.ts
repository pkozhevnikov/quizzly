import { HttpClient } from '@angular/common/http'
import { Injectable } from '@angular/core'
import { ID } from '@datorama/akita'
import { tap } from 'rxjs/operators'
import { Exam, CreateExam } from './exam.model'
import { ExamsStore } from './exams.store'
import { HttpBasedService } from "../../util/httpbased.service"
import { GlobalConfig } from "../../global.config"
import { SessionQuery } from "../../session/state/session.query"
import { UiStore } from "../../ui.store"
import { QuizzesQuery } from "../../quizzes/state/quizzes.query"
import { QuizzesStore } from "../../quizzes/state/quizzes.store"

@Injectable({ providedIn: 'root' })
export class ExamsService extends HttpBasedService {

  constructor(
    config: GlobalConfig,
    http: HttpClient,
    sessionQuery: SessionQuery,
    uiStore: UiStore,
    private examsStore: ExamsStore,
    private quizzesQuery: QuizzesQuery,
    private quizzesStore: QuizzesStore
  ) {
    super(config.baseApiUrl, http, sessionQuery, uiStore)
  }


  get() {
    this.request(this.GET, "exam", {200: l => this.examsStore.set(l)})
  }

  create(createReq: CreateExam) {
    const quiz = this.quizzesQuery.getEntity(createReq.quizId)
    if (!quiz)
      this.uiStore.warn(`Quiz [${createReq.quizId}] not found`)
    else
      this.request(this.POST, "exam", {200: d => {
        this.examsStore.add({
          id: createReq.id,
          quiz: {id: quiz.id, title: quiz.title},
          period: {start: createReq.start, end: createReq.end},
          host: d.host,
          state: "Pending",
          trialLength: createReq.trialLength,
          prestartAt: d.prepartationStart
        })
        this.quizzesStore.update({id: createReq.id, inUse: true})
      }}, createReq)
  }

  cancel(id: string) {
    this.request(this.DELETE, `exam/${id}`, {204: _ => {
      this.uiStore.info("Exam cancelled")
      this.examsStore.update({id, state: "Cancelled"})
    }})
  }

  changeTrialLength(id: string, length: number) {
    this.request(
      this.POST, 
      `exam/${id}`, 
      {204: _ => this.uiStore.info("Trial length changed")},
      {length}
    )
  }

  includeTestees(

}
