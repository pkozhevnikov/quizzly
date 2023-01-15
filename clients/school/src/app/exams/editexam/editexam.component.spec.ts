import { fakeAsync, tick, 
        ComponentFixture, TestBed, ComponentFixtureAutoDetect } from '@angular/core/testing'
import { ActivatedRoute } from "@angular/router"
import { PersonsState } from "../../persons.state"
import { ExamsService } from "../state/exams.service"
import { ExamsQuery } from "../state/exams.query"
import { FormsModule, ReactiveFormsModule } from "@angular/forms"
import { formatDate, DATE_PIPE_DEFAULT_TIMEZONE } from "@angular/common" 

import { matchers } from "../../util/matchers"
import { of } from "rxjs"

import { EditexamComponent } from './editexam.component'
import { PersonchooserComponent } from "../personchooser/personchooser.component"

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
      declarations: [ EditexamComponent, PersonchooserComponent ],
      providers: [
        {provide: ComponentFixtureAutoDetect, useValue: true},
        {provide: PersonsState, useValue: jasmine.createSpyObj("PersonsState", ["selectAll"])},
        {provide: ExamsService, useValue: jasmine.createSpyObj("ExamsService", 
            ["changeTrialLength", "getTestees", "includeTestees", "excludeTestees"])},
        {provide: ExamsQuery, useValue: jasmine.createSpyObj("ExamsQuery", ["getEntity"])},
        {provide: ActivatedRoute, useValue: { params: of({id: "pending"}) } },
        {provide: DATE_PIPE_DEFAULT_TIMEZONE, useValue: "UTC"},
      ],
      imports: [
        FormsModule, ReactiveFormsModule, 
      ]
    })
    .compileComponents()

    personsState = TestBed.inject(PersonsState) as jasmine.SpyObj<PersonsState>
    examsService = TestBed.inject(ExamsService) as jasmine.SpyObj<ExamsService>
    examsQuery = TestBed.inject(ExamsQuery) as jasmine.SpyObj<ExamsQuery>
    examsQuery.getEntity.withArgs("pending").and.returnValue(examPending)
    personsState.selectAll.withArgs("").and.returnValue(of(testpersons))
    examsService.getTestees.withArgs("pending").and
      .returnValue(Promise.resolve([testpersons[2], testpersons[4]]))


    fixture = TestBed.createComponent(EditexamComponent)
    component = fixture.componentInstance
    node = fixture.nativeElement
  })

  it('should create', () => {
    expect(component).toBeTruthy()
  })

  it ("initialiizes correctly", () => {
    expect(personsState.selectAll).toHaveBeenCalledWith("")
    expect(examsService.getTestees).toHaveBeenCalledWith("pending")
    expect(examsQuery.getEntity).toHaveBeenCalledWith("pending")
    expect(node.querySelector(".quiz-id")).toHaveText("q1")
    expect(node.querySelector(".quiz-title")).toHaveText("q1 title")
    const start = formatDate(examPending.period.start, "MMMM d, y, HH:mm", "en-US", "UTC")
    const end = formatDate(examPending.period.end, "MMMM d, y, HH:mm", "en-US", "UTC")
    expect(node.querySelector(".exam-start")).toHaveText(start)
    expect(node.querySelector(".exam-end")).toHaveText(end)
    expect(node.querySelector(".host")).toHaveText("off1 name")
    expect(node.querySelector(".state")).toHaveText("Pending")
    const trialLength: HTMLInputElement = node.querySelector(".trial-length")!
    expect(trialLength.value).toEqual("45")
    const prestart = formatDate(examPending.prestartAt, "MMMM d, y, HH:mm", "en-US", "UTC")
    expect(node.querySelector(".prestart")).toHaveText(prestart)
    expect(node.querySelectorAll(".persons-src .person")).toHaveSize(6)
    for (let i = 0; i < testpersons.length; i++) {
      expect(node.querySelectorAll(".persons-src .person")[i]).toHaveText(testpersons[i].name)
    }
    expect(node.querySelectorAll(".testees .person")).toHaveSize(2)
    expect(node.querySelectorAll(".testees .person")[0]).toHaveText("off3 name")
    expect(node.querySelectorAll(".testees .person")[1]).toHaveText("stud2 name")
  })

  it ("sends exclude testees request and updates testee list on exclusion", fakeAsync( () => {
    node.querySelectorAll(".testees .person").forEach(elem => (elem as HTMLElement).click())
    examsService.excludeTestees.and.returnValue(Promise.resolve([testpersons[2], testpersons[4]]))
    component.testeesToExclude = [testpersons[2], testpersons[4]]
    const excludeButton: HTMLElement = node.querySelector(".exclude")!
    excludeButton.click()
    expect(examsService.excludeTestees).toHaveBeenCalledWith("pending", ["off3", "stud2"])
    tick()
    expect(node.querySelectorAll(".testees .person")).toHaveSize(0)
  }))

  it ("sends include testees request and updates testee list on inclusion", fakeAsync( () => {
    examsService.includeTestees.and.returnValue(Promise.resolve([testpersons[3]]))
    const includeButton: HTMLElement = node.querySelector(".include")!
    component.testeesToInclude = [testpersons[3], testpersons[5]]
    includeButton.click()
    expect(examsService.includeTestees).toHaveBeenCalledWith("pending", ["stud1", "stud3"])
    tick()
    const testees = node.querySelectorAll(".testees .person")
    expect(testees).toHaveSize(3)
    expect(testees[0]).toHaveText("off3 name")
    expect(testees[1]).toHaveText("stud2 name")
    expect(testees[2]).toHaveText("stud1 name")
  }))

  it ("sends change trial length request", () => {
    const lengthBox: HTMLInputElement = node.querySelector(".trial-length")!
    lengthBox.value = "72"
    lengthBox.dispatchEvent(new Event("input"))
    const changeLengthButton: HTMLElement = node.querySelector(".change-length")!
    changeLengthButton.click()
    expect(examsService.changeTrialLength).toHaveBeenCalledWith("pending", 72)
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

