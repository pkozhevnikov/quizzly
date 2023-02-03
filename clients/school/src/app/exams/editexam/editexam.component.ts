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
  passingGrade = new FormControl("")

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
        this.passingGrade.setValue(String(e.passingGrade))
        this.examsService.getTestees(e.id)
          .then(tl => this.testees = tl)
      }
    })
    this.personsState.init()
  }

  changeAttrs() {
    this.examsService.changeTrialAttrs(
      this.exam.id, 
      Number(this.trialLength.value),
      Number(this.passingGrade.value)
    )
  }

  setTesteesToInclude(testees: Person[]) {
    this.testeesToInclude = testees
  }

  includeTestees() {
    this.examsService.includeTestees(this.exam.id, this.testeesToInclude.map(p => p.id))
      .then(included => {
        this.testees = this.testees.concat(included)
        this.testeesToInclude = []
      })
  }

  excludeTestees() {
    this.examsService.excludeTestees(this.exam.id, this.testeesToExclude.map(p => p.id))
      .then(excluded => {
        this.testeesToExclude = []
        const eids = excluded.map(t => t.id)
        this.testees = this.testees.filter(t => !eids.includes(t.id))
      })
  }
  
}
