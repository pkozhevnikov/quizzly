import { HttpRequest, HttpResponse, HttpHeaders, HttpErrorResponse, HttpClient } from '@angular/common/http'
import { map, tap, catchError, filter } from 'rxjs/operators'
import { throwError, Observable } from "rxjs"

import { SessionQuery } from "../session/state/session.query"
import { UiStore } from "../ui.store"

export interface Done {}
export const DONE: Done = {}

interface ResponseActions {
  [key: number]: (resp: any) => void
}

export class HttpBasedService {

  private baseApiUrl: string
  private http: HttpClient
  protected sessionQuery: SessionQuery
  protected uiStore: UiStore

  protected readonly GET = "GET"
  protected readonly POST = "POST"
  protected readonly PUT = "PUT"
  protected readonly PATCH = "PATCH"
  protected readonly DELETE = "DELETE"

  constructor(
    baseApiUrl: string,
    http: HttpClient,
    sessionQuery: SessionQuery,
    uiStore: UiStore
  ) {
    this.baseApiUrl = baseApiUrl
    this.http = http
    this.sessionQuery = sessionQuery
    this.uiStore = uiStore
    sessionQuery.userId$.subscribe(id => {
      if (id)
        this.headers = new HttpHeaders().append("p", id)
      else
        this.headers = null
    })
  }

  private handleError(error: HttpErrorResponse) {
    let msg: string
    if (error.status == 0) {
      msg = `Error requesting quiz list: ${error.error}`
    } else if (error.status == 401) {
      msg = "Access denied"
    } else if (error.status == 422) {
      const err = error.error
      msg = `(${err.reason.code}) ${err.reason.phrase}: ${JSON.stringify(err.clues)}`
    } else {
      msg = error.message
    }
    return throwError(msg)
  }

  private headers: HttpHeaders | null = null

  private req(method: string, path: string, body?: any) {
    if (body)
      return new HttpRequest(method, `${this.baseApiUrl}/${path}`, body, {
        headers: this.headers!
      })
    else {
      return new HttpRequest(method, `${this.baseApiUrl}/${path}`, {}, {
        headers: this.headers!
      })
    }
  }


  request(method: string, path: string, actions: ResponseActions, body?: any): Promise<Done> {
    if (this.headers === null) {
      this.uiStore.error("Not logged in")
      return Promise.reject("Not logged in")
    }
    let res: any
    let rej: any
    const p = new Promise<Done>((resolve, reject) => {
      res = resolve
      rej = reject
    })
    this.http.request(this.req(method, path, body))
      .pipe(catchError(this.handleError))
      .pipe(filter(e => e.type == 4))
      .pipe(map(e => e as HttpResponse<any>))
      .subscribe({
        next: resp => {
          if (actions && actions[resp.status]) {
            actions[resp.status](resp.body)
            res(DONE)
          } else {
            const message = `action for ${method} ${path} ${resp.status} not specified`
            console.log(message)
            this.uiStore.warn(message)
            rej(message)
          }
        }, 
        error: msg => {
          this.uiStore.error(msg)
          rej(msg)
        }
      })
    return p
  }


}
