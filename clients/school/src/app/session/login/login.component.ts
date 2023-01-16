import { Component, OnInit } from '@angular/core'
import { Router } from "@angular/router"
import { FormControl } from "@angular/forms"

import { filter } from "rxjs"

import { SessionService } from "../state/session.service"
import { SessionQuery } from "../state/session.query"

@Component({
  selector: 'app-login',
  templateUrl: './login.component.html',
  styleUrls: ['./login.component.css']
})
export class LoginComponent implements OnInit {

  constructor(
    private sessionService: SessionService, 
    private sessionQuery: SessionQuery,
    private router: Router
  ) { }

  ngOnInit(): void {
    this.sessionQuery.loggedIn$.pipe(filter(v => v))
      .subscribe(change => this.router.navigate(["/quizzes"]))
  }

  uname = new FormControl("")
  pword = new FormControl("")

  tryLogin() {
    this.sessionService.login(this.uname.value!, this.pword.value!)
  }

}
