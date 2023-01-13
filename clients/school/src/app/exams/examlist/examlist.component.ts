import { Component, OnInit } from '@angular/core'

import { ExamsService } from "../state/exams.service"
import { ExamsQuery } from "../state/exams.query"

@Component({
  selector: 'app-examlist',
  templateUrl: './examlist.component.html',
  styleUrls: ['./examlist.component.css']
})
export class ExamlistComponent implements OnInit {

  list$ = this.examsQuery.selectAll()

  constructor(
    private examsService: ExamsService,
    private examsQuery: ExamsQuery
  ) { }

  ngOnInit(): void {
    this.examsService.get()
  }

  cancel(id: string) {
    this.examsService.cancel(id)
  }

}
