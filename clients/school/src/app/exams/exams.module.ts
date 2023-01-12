import { NgModule } from '@angular/core';
import { CommonModule } from '@angular/common';
import { NewexamComponent } from './newexam/newexam.component';
import { EditexamComponent } from './editexam/editexam.component';
import { ExamlistComponent } from './examlist/examlist.component';



@NgModule({
  declarations: [
    NewexamComponent,
    EditexamComponent,
    ExamlistComponent
  ],
  imports: [
    CommonModule
  ]
})
export class ExamsModule { }
