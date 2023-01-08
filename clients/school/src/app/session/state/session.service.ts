import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { tap } from 'rxjs/operators';
import { SessionStore } from './session.store';

@Injectable({ providedIn: 'root' })
export class SessionService {

  constructor(private sessionStore: SessionStore, private http: HttpClient) {
  }

  private const fakeOfficials = {
    off1: {id: "off1", name: "off1 name"},
    off2: {id: "off2", name: "off2 name"},
    off3: {id: "off3", name: "off3 name"},
    off4: {id: "off4", name: "off4 name"},
    off5: {id: "off5", name: "off5 name"}
  }

  login(username: string, password: string) {
    if (fakeOfficials[username]) {
      this.sessionStore.update(fakeOfficials[username])
    } else {
      //put message
    }
  }

}
