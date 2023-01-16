import { NgModule } from "@angular/core"
import { RouterModule, Routes,
      UrlSegment, UrlSegmentGroup, UrlTree} from "@angular/router"
import { LoginComponent } from "./session/login/login.component"
import { QuizlistComponent } from "./quizzes/quizlist/quizlist.component"
import { NewexamComponent } from "./exams/newexam/newexam.component"
import { ExamlistComponent } from "./exams/examlist/examlist.component"
import { EditexamComponent } from "./exams/editexam/editexam.component"

export const routes: Routes = [
  {path: "login", component: LoginComponent},
  {path: "quizzes", component: QuizlistComponent, canActivate: [CommonCanActivate]},
  {path: "newexam/:quizId", component: NewexamComponent, canActivate: [CommonCanActivate]},
  {path: "exam/:id", component: EditexamComponent, canActivate: [CommonCanActivate]},
  {path: "exam", component: ExamlistComponent, canActivate: [CommonCanActivate]},
  {path: "", redirectTo: "/quizzes", pathMatch: "full"},
  {path: "**", component: QuizlistComponent},
]

@Injectable({provideIn: "root"})
export class CommonCanActivate implements CanActivate {
  private loginUrl = new UrlTree(new UrlSegmentGroup([new UrlSegment("login")]))
  constructor(
    private sessionQuery: SessionQuery
  ) {}

  canActivate(
    route: ActivatedRoutSnapshot,
    state: RouterStateSnaphot
  ) {
    return this.sessionQuery.loggedIn$
      .pipe(map(loggedin => {
        if (loggedin)
          return true
        else
          return loginUrl
      }))
  }
}

@NgModule({
  imports: [RouterModule.forRoot(routes)],
  exports: [RouterModule]
})
export class AppRoutingModule { }
