import { NgModule } from "@angular/core";
import { CommonModule } from "@angular/common";
import { NewexamComponent } from "./newexam/newexam.component";
import { EditexamComponent } from "./editexam/editexam.component";
import { ExamlistComponent } from "./examlist/examlist.component";
import { PersonchooserComponent } from "./personchooser/personchooser.component";



@NgModule({
  declarations: [
    NewexamComponent,
    EditexamComponent,
    ExamlistComponent,
    PersonchooserComponent
  ],
  imports: [
    CommonModule
  ]
})
export class ExamsModule { }
