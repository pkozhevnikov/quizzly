import { Component, OnInit } from '@angular/core'
import { FormControl } from "@angular/forms"
import { ActivatedRoute } from "@angular/router"

import { Person } from "../../persons.state"
import { ExamsQuery } from "../state/exams.query"
import { ExamsService } from "../state/exams.service"
import { Exam, createExam } from "../state/exam.model"
import { PersonsState } from "../../persons.state"

@Component({
  selector: 'app-editexam',
  templateUrl: './editexam.component.html',
  styleUrls: ['./editexam.component.css']
})
export class EditexamComponent implements OnInit {

  testeesToInclude: Person[] = []
  testees: Person[] = []
  testeesToExclude: Person[] = []
  exam: Exam = createExam({})
  trialLength = new FormControl("")

  constructor(
    private examsQuery: ExamsQuery,
    private examsService: ExamsService,
    private route: ActivatedRoute,
    private personsState: PersonsState
  ) { }

  ngOnInit(): void {
    this.route.params.subscribe(params => {
      const e = this.examsQuery.getEntity(params["id"])
      if (e) {
        this.exam = e
        this.trialLength.setValue(String(e.trialLength))
        this.examsService.getTestees(e.id)
          .then(tl => this.testees = tl)
      }
    })
    this.personsState.get()
  }

  changeLength() {
    this.examsService.changeTrialLength(this.exam.id, Number(this.trialLength.value))
  }

  setTesteesToInclude(testees: Person[]) {
    this.testeesToInclude = testees
  }

  includeTestees() {
    this.examsService.includeTestees(this.exam.id, this.testeesToInclude.map(p => p.id))
      .then(included => this.testees = this.testees.concat(included))
  }

  excludeTestees() {
    this.examsService.excludeTestees(this.exam.id, this.testeesToExclude.map(p => p.id))
      .then(excluded => this.testees = this.testees.filter(t => !excluded.includes(t)))
  }
  
}
