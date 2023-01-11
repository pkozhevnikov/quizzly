import { HttpClient } from '@angular/common/http'
import { Injectable } from '@angular/core'
import { ID } from '@datorama/akita'
import { tap } from 'rxjs/operators'
import { Quiz } from './quiz.model'
import { QuizzesStore } from './quizzes.store'

import { SessionQuery } from "../../session/state/session.query"
import { UiStore } from "../../ui.store"
import { HttpBasedService } from "../../util/httpbased.service"

@Injectable({ providedIn: 'root' })
export class QuizzesService extends HttpBasedService {

  constructor(
    baseApiUrl: string,
    http: HttpClient,
    sessionQuery: SessionQuery,
    uiStore: UiStore,
    private quizzesStore: QuizzesStore, 
  ) {
    super(baseApiUrl, http, sessionQuery, uiStore)
  }


  get() {
    //this.get<Quiz[]>("quiz", {200: )
    //  .subscribe(list => this.quizzesStore.set(list))
  }

  publish(id: string) {
  }

  unpublish(id: string) {
  }

}
