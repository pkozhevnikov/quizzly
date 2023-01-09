import { Injectable } from '@angular/core'
import { Store, StoreConfig } from '@datorama/akita'

export interface SessionState {
  id: string | null
  name: string | null
}

export function createInitialState(): SessionState {
  return {
    id: null,
    name: null
  }
}

@Injectable({ providedIn: 'root' })
@StoreConfig({ name: 'session' })
export class SessionStore extends Store<SessionState> {

  constructor() {
    super(createInitialState())
  }

}
