import { Component } from '@angular/core'
import { UiQuery } from "./ui.query"
import { SessionQuery } from "./session/state/session.query"

@Component({
  selector: 'app-root',
  templateUrl: "./app.component.html"
  //template: "<router-outlet></router-outlet>"
})
export class AppComponent {
  constructor(
    public uiQuery: UiQuery,
    public sessionQuery: SessionQuery
  ) {}
  title = 'Quizzly :: Exam management'
}
