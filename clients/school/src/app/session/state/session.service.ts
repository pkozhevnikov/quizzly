import { HttpClient } from '@angular/common/http'
import { Injectable } from '@angular/core'
import { tap } from 'rxjs/operators'
import { SessionStore, SessionState } from './session.store'
import { UiStore, Notif } from '../../ui.store'

interface OffList {
  [key: string]: SessionState
}

@Injectable({ providedIn: 'root' })
export class SessionService {

  constructor(
    private sessionStore: SessionStore, 
    private http: HttpClient,
    private uiStore: UiStore
  ) {
  }

  private readonly fakeOfficials: OffList = {
    off1: {id: "off1", name: "off1 name"},
    off2: {id: "off2", name: "off2 name"},
    off3: {id: "off3", name: "off3 name"},
    off4: {id: "off4", name: "off4 name"},
    off5: {id: "off5", name: "off5 name"}
  }

  login(username: string, password: string) {
    console.log(`try to login ${username} ${password}`)
    if (this.fakeOfficials[username]) {
      console.log("set success")
      this.sessionStore.update(this.fakeOfficials[username])
      this.uiStore.info("Successfully logged in")
    } else {
      console.log("set wrong pw")
      this.uiStore.error("Wrong username or password")
    }
  }

  logout() {
    this.sessionStore.update({id: null, name: null})
  }


}
