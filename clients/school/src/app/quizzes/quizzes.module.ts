import { NgModule } from "@angular/core"
import { CommonModule } from "@angular/common"
import { FormsModule, ReactiveFormsModule } from "@angular/forms"
import { RouterModule } from "@angular/router"
import { QuizlistComponent } from "./quizlist/quizlist.component"



@NgModule({
  declarations: [
    QuizlistComponent
  ],
  imports: [
    RouterModule,
    FormsModule,
    ReactiveFormsModule,
    CommonModule
  ]
})
export class QuizzesModule { }
