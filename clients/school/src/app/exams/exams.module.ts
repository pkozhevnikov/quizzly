import { NgModule } from "@angular/core"
import { CommonModule } from "@angular/common"
import { FormsModule, ReactiveFormsModule } from "@angular/forms"
import { RouterModule } from "@angular/router"
import { NgxDaterangepickerMd } from "ngx-daterangepicker-material"
import { NewexamComponent } from "./newexam/newexam.component"
import { EditexamComponent } from "./editexam/editexam.component"
import { ExamlistComponent } from "./examlist/examlist.component"
import { PersonchooserComponent } from "./personchooser/personchooser.component"



@NgModule({
  declarations: [
    NewexamComponent,
    EditexamComponent,
    ExamlistComponent,
    PersonchooserComponent
  ],
  imports: [
    RouterModule,
    FormsModule,
    ReactiveFormsModule,
    NgxDaterangepickerMd.forRoot(),
    CommonModule
  ]
})
export class ExamsModule { }
