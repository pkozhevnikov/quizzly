import { Component } from '@angular/core'
import { UiQuery } from "./ui.query"
import { SessionQuery } from "./session/state/session.query"
import { SessionService } from "./session/state/session.service"

@Component({
  selector: 'app-root',
  templateUrl: "./app.component.html",
  styleUrls: ["./app.component.css"]
})
export class AppComponent {
  constructor(
    public uiQuery: UiQuery,
    public sessionQuery: SessionQuery,
    public sessionService: SessionService
  ) {}
  title = 'Quizzly :: Exam management'

  logout() {
    this.sessionService.logout()
  }

}
