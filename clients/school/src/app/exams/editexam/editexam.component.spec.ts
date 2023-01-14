import { ComponentFixture, TestBed, ComponentFixtureAutoDetect } from '@angular/core/testing'
import { ActivatedRoute } from "@angular/router"
import { PersonsState } from "../../persons.state"
import { ExamsService } from "../state/exams.service"
import { ExamsQuery } from "../state/exams.query"
import { FormsModule, ReactiveFormsModule } from "@angular/forms"
import { formatDate, DATE_PIPE_DEFAULT_TIMEZONE } from "@angular/common" 

import { matchers } from "../../util/matchers"
import { of } from "rxjs"

import { EditexamComponent } from './editexam.component'

import { examPending } from "../state/exams.service.spec"

describe('EditexamComponent', () => {
  let component: EditexamComponent
  let fixture: ComponentFixture<EditexamComponent>

  let personsState: jasmine.SpyObj<PersonsState>
  let examsService: jasmine.SpyObj<ExamsService>
  let examsQuery: jasmine.SpyObj<ExamsQuery>
  let node: HTMLElement

  beforeEach(async () => {
    jasmine.addMatchers(matchers)
    await TestBed.configureTestingModule({
      declarations: [ EditexamComponent ],
      providers: [
        {provide: ComponentFixtureAutoDetect, useValue: true},
        {provide: PersonsState, useValue: jasmine.createSpyObj("PersonsState", ["selectAll"])},
        {provide: ExamsService, useValue: jasmine.createSpyObj("ExamsService", 
            ["changeTrialLength", "getTestees", "includeTestees", "excludeTestees"])},
        {provide: ExamsQuery, useValue: jasmine.createSpyObj("ExamsQuery", ["getEntity"])},
        {provide: ActivatedRoute, useValue: { params: of({id: "q1-e1"}) } },
      ],
      imports: [
        FormsModule, ReactiveFormsModule, 
      ]
    })
    .compileComponents()

    personsState = TestBed.inject(PersonsState) as jasmine.SpyObj<PersonsState>
    examsService = TestBed.inject(ExamsService) as jasmine.SpyObj<ExamsService>
    examsQuery = TestBed.inject(ExamsQuery) as jasmine.SpyObj<ExamsQuery>
    examsQuery.getEntity.withArgs("q1-e1").and.returnValue(examPending)
    personsState.selectAll.withArgs("").and.returnValue(of(testpersons))
    examsService.getTestees.withArgs("q1-e1").and.returnValue(of([testpersons[0], testpersons[4]]))


    fixture = TestBed.createComponent(EditexamComponent)
    component = fixture.componentInstance
    node = fixture.nativeElement
  })

  it('should create', () => {
    expect(component).toBeTruthy()
  })

  xit ("initialiizes correctly", () => {
    expect(personsState.selectAll).toHaveBeenCalledWith("")
    expect(examsService.getTestees).toHaveBeenCalledWith("q1-e1")
    expect(examsQuery.getEntity).toHaveBeenCalledWith("q1-e1")
    expect(node.querySelector(".quiz-id")).toHaveText("q1")
    expect(node.querySelector(".quiz-title")).toHaveText("q1 title")
    const start = formatDate(examPending.period.start, "MMMM d, y, HH:mm", "en-US", "UTC")
    const end = formatDate(examPending.period.end, "MMMM d, y, HH:mm", "en-US", "UTC")
    expect(node.querySelector(".exam-start")).toHaveText(start)
    expect(node.querySelector(".exam-end")).toHaveText(end)
    expect(node.querySelector(".host")).toHaveText("off1 name")
    expect(node.querySelector(".state")).toHaveText("Pending")
    const prestart = formatDate(examPending.prestartAt, "MMMM d, y, HH:mm", "en-US", "UTC")
    expect(node.querySelector(".prestart")).toHaveText(prestart)
    expect(node.querySelectorAll(".person-src .person")).toHaveSize(6)
    for (let i = 0; i < testpersons.length; i++) {
      expect(node.querySelectorAll(".person-src .person")[i]).toHaveText(testpersons[i].name)
    }
    expect(node.querySelectorAll(".testees .person")).toHaveSize(2)
    expect(node.querySelectorAll(".testees .person")[0]).toHaveText("off3 name")
    expect(node.querySelectorAll(".testees .person")[1]).toHaveText("stud2 name")
  })

})


const testpersons = [
  {id: "off1", name: "off1 name", place: "Official"},
  {id: "off2", name: "off2 name", place: "Official"},
  {id: "off3", name: "off3 name", place: "Official"},
  {id: "stud1", name: "stud1 name", place: "Student"},
  {id: "stud2", name: "stud2 name", place: "Student"},
  {id: "stud3", name: "stud3 name", place: "Student"},
]

