import { Component, OnInit } from '@angular/core'
import { Router } from "@angular/router"

import { filter } from "rxjs"

import { SessionService } from "../state/session.service"
import { SessionQuery } from "../state/session.query"
import { UiQuery } from "../../ui.query"

@Component({
  selector: 'app-login',
  templateUrl: './login.component.html',
  styleUrls: ['./login.component.css']
})
export class LoginComponent implements OnInit {

  notif$ = this.uiQuery.notif$

  constructor(
    private sessionService: SessionService, 
    private sessionQuery: SessionQuery,
    private uiQuery: UiQuery,
    private router: Router
  ) { }

  ngOnInit(): void {
    this.sessionQuery.loggedIn$.pipe(filter(v => v))
      .subscribe(change => this.router.navigate(["/quizzes"]))
  }

  username = 'x'
  password = 'y'

  tryLogin() {
    this.sessionService.login(this.username, this.password)
  }

}
