import { NgModule } from '@angular/core'
import { RouterModule, Routes } from '@angular/router'
import { LoginComponent } from "./session/login/login.component"
import { QuizlistComponent } from "./quizzes/quizlist/quizlist.component"
import { NewexamComponent } from "./exams/newexam/newexam.component"
import { ExamlistComponent } from "./exams/examlist/examlist.component"
import { EditexamComponent } from "./exams/editexam/editexam.component"

export const routes: Routes = [
  {path: "login", component: LoginComponent},
  {path: "quizzes", component: QuizlistComponent},
  {path: "newexam/:quizId", component: NewexamComponent},
  {path: "exam", component: ExamlistComponent},
  {path: "exam/:id", component: EditexamComponent},
]

@NgModule({
  imports: [RouterModule.forRoot(routes)],
  exports: [RouterModule]
})
export class AppRoutingModule { }
