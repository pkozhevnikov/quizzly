import { HttpClient } from '@angular/common/http'
import { Injectable } from '@angular/core'
import { ID } from '@datorama/akita'
import { tap } from 'rxjs/operators'
import { Quiz } from './quiz.model'
import { QuizzesStore } from './quizzes.store'
import { QuizzesQuery } from "./quizzes.query"

import { SessionQuery } from "../../session/state/session.query"
import { UiStore } from "../../ui.store"
import { HttpBasedService } from "../../util/httpbased.service"
import { GlobalConfig } from "../../global.config"

@Injectable({ providedIn: 'root' })
export class QuizzesService extends HttpBasedService {

  constructor(
    config: GlobalConfig,
    http: HttpClient,
    sessionQuery: SessionQuery,
    uiStore: UiStore,
    private quizzesStore: QuizzesStore, 
    private quizzesQuery: QuizzesQuery
  ) {
    super(config.baseApiUrl, http, sessionQuery, uiStore)
  }


  get() {
    this.request(this.GET, "quiz", {200: list => this.quizzesStore.set(list)})
  }

  publish(id: string) {
    const quiz = this.quizzesQuery.getEntity(id)
    if (!quiz)
      this.uiStore.warn(`[${id}] not found`)
    else if (quiz.inUse)
      this.uiStore.warn(`[${id}] is being used`)
    else if (quiz.isPublished)
      this.uiStore.warn(`[${id}] is already published`)
    else
      this.request(this.PATCH, `quiz/${id}`, {
        204: _ => this.quizzesStore.update(id, {isPublished: true, everPublished: true})
      })
  }

  unpublish(id: string) {
    const quiz = this.quizzesQuery.getEntity(id)
    if (!quiz)
      this.uiStore.warn(`[${id}] not found`)
    else if (!quiz.isPublished)
      this.uiStore.warn(`[${id}] is not published`)
    else
      this.request(this.DELETE, `quiz/${id}`, {
        204: _ => this.quizzesStore.update(id, {isPublished: false})
      })
  }

}
