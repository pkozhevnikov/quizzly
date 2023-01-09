import { Injectable } from '@angular/core'
import { Query } from '@datorama/akita'
import { SessionStore, SessionState } from './session.store'

import { map } from "rxjs"

@Injectable({ providedIn: 'root' })
export class SessionQuery extends Query<SessionState> {

  loggedIn$ = this.select("id").pipe(map(v => !!v))
  userId$ = this.select("id")
  userName$ = this.select("name")

  constructor(protected override store: SessionStore) {
    super(store)
  }

}
