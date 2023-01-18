import { Injectable, NgModule } from "@angular/core"
import { RouterModule, Routes, CanActivate, ActivatedRouteSnapshot, RouterStateSnapshot,
      UrlTree, Router } from "@angular/router"
import { LoginComponent } from "./session/login/login.component"
import { QuizlistComponent } from "./quizzes/quizlist/quizlist.component"
import { NewexamComponent } from "./exams/newexam/newexam.component"
import { ExamlistComponent } from "./exams/examlist/examlist.component"
import { EditexamComponent } from "./exams/editexam/editexam.component"
import { SessionQuery } from "./session/state/session.query"
import { map, Observable } from "rxjs"

@Injectable({providedIn: "root"})
export class CommonCanActivate implements CanActivate {
  constructor(
    private sessionQuery: SessionQuery,
    private router: Router
  ) {
  }

  canActivate(
    route: ActivatedRouteSnapshot,
    state: RouterStateSnapshot
  ): Observable<boolean | UrlTree> {
    return this.sessionQuery.loggedIn$
      .pipe(map(loggedin => loggedin || this.router.createUrlTree(["/login"])))
  }
}

export const routes: Routes = [
  {path: "login", component: LoginComponent},
  {path: "quizzes", component: QuizlistComponent, canActivate: [CommonCanActivate]},
  {path: "newexam/:quizId", component: NewexamComponent, canActivate: [CommonCanActivate]},
  {path: "exam/:id", component: EditexamComponent, canActivate: [CommonCanActivate]},
  {path: "exam", component: ExamlistComponent, canActivate: [CommonCanActivate]},
  {path: "", redirectTo: "/quizzes", pathMatch: "full"},
  {path: "**", component: QuizlistComponent, canActivate: [CommonCanActivate]},
]

@NgModule({
  imports: [RouterModule.forRoot(routes)],
  exports: [RouterModule],
})
export class AppRoutingModule { }
