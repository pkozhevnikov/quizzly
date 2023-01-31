import { fakeAsync, tick, ComponentFixture, TestBed, ComponentFixtureAutoDetect } from '@angular/core/testing'
import { ActivatedRoute, Router } from "@angular/router"
import { RouterTestingModule } from "@angular/router/testing"
import { NgxDaterangepickerMd } from "ngx-daterangepicker-material"
import { PersonsState } from "../../persons.state"
import { QuizzesQuery } from "../../quizzes/state/quizzes.query"
import { ExamsService } from "../state/exams.service"
import { FormsModule, ReactiveFormsModule } from "@angular/forms"

import { of } from "rxjs"
import { matchers } from "../../util/matchers"
import * as dayjs from "dayjs"

import { NewexamComponent } from './newexam.component'
import { PersonchooserComponent } from "../personchooser/personchooser.component"
import { ExamsModule } from "../exams.module"

describe('NewexamComponent', () => {
  let component: NewexamComponent
  let fixture: ComponentFixture<NewexamComponent>
  let router: Router

  let quizzesQuery: jasmine.SpyObj<QuizzesQuery>
  let personsState: jasmine.SpyObj<PersonsState>
  let examsService: jasmine.SpyObj<ExamsService>
  let node: HTMLElement

  beforeEach(async () => {
    jasmine.addMatchers(matchers)
    await TestBed.configureTestingModule({
      declarations: [ NewexamComponent, PersonchooserComponent ],
      providers: [
        {provide: ComponentFixtureAutoDetect, useValue: true},
        {provide: QuizzesQuery, useValue: jasmine.createSpyObj("QuizzesQuery", ["getEntity"])},
        {provide: PersonsState, useValue: jasmine.createSpyObj("PersonsState", ["selectAll", "init"])},
        {provide: ExamsService, useValue: jasmine.createSpyObj("ExamsService", ["create"])},
        {provide: ActivatedRoute, useValue: { params: of({quizId: "q1"}) } },
      ],
      imports: [
        FormsModule, ReactiveFormsModule, 
        NgxDaterangepickerMd.forRoot(),
        RouterTestingModule,
        //ExamsModule
      ]
    })
    .compileComponents()

    quizzesQuery = TestBed.inject(QuizzesQuery) as jasmine.SpyObj<QuizzesQuery>
    personsState = TestBed.inject(PersonsState) as jasmine.SpyObj<PersonsState>
    examsService = TestBed.inject(ExamsService) as jasmine.SpyObj<ExamsService>
    examsService.create.and.stub()
    personsState.selectAll.withArgs("").and.returnValue(of(testpersons))
    personsState.init.and.stub()
    quizzesQuery.getEntity.withArgs("q1").and.returnValue({
      id: "q1", title: "q1 title", obsolete: false, inUse: false, 
      isPublished: false, everPublished: false, recommendedTrialLength: 45
    })
    router = TestBed.inject(Router)
    spyOn(router, "navigate")

    fixture = TestBed.createComponent(NewexamComponent)
    component = fixture.componentInstance
    node = fixture.nativeElement

  })

  it ("requests quiz locally", () => {
    expect(quizzesQuery.getEntity).toHaveBeenCalledWith("q1")
    expect(node.querySelector(".quiz-id")).toHaveText("q1")
    expect(node.querySelector(".quiz-title")).toHaveText("q1 title")
    const lengthBox: HTMLInputElement = node.querySelector(".trial-length")!
    expect(lengthBox.value).toEqual("45")
  })

  it ("loads person list", () => {
    expect(personsState.init).toHaveBeenCalled()
    expect(personsState.selectAll).toHaveBeenCalled()
    const personsSrc = node.querySelector(".persons-src")!
    expect(personsSrc.querySelectorAll(".person")).toHaveSize(testpersons.length)
    for (let i = 0; i < testpersons.length; i++) {
      expect(personsSrc.querySelectorAll(".person")[i]).toHaveText(testpersons[i].name)
    }
  })

  it ("filters person list", () => {
    const filterBox: HTMLInputElement = node.querySelector(".persons-filter")!
    const personsSrc = node.querySelector(".persons-src")!
    expect(personsSrc.querySelectorAll(".person").length).toEqual(testpersons.length)
    const filtered = testpersons.filter(p => p.id.startsWith("off"))
    personsState.selectAll.withArgs("off").and.returnValue(of(testpersons.slice(0, 3)))
    filterBox.value = "off"
    filterBox.dispatchEvent(new Event("input"))
    expect(personsSrc.querySelectorAll(".person")!.length).toEqual(3)
    for (let i = 0; i < 3; i++) {
      expect(personsSrc.querySelectorAll(".person")[i]).toHaveText(filtered[i].name)
    }
  })

  it ("moves selected persons to selection and removes from selection", fakeAsync( () => {
    const personsSrc: HTMLSelectElement = node.querySelector(".persons-src")!
    personsSrc.options[1].selected = true
    personsSrc.options[3].selected = true
    personsSrc.options[5].selected = true
    personsSrc.dispatchEvent(new Event("change"))
    const selectButton: HTMLButtonElement = node.querySelector(".persons-select")!
    selectButton.click()
    expect(node.querySelectorAll(".person-selected").length).toEqual(3)
    expect(component.selectedPersons).toEqual([testpersons[1], testpersons[3], testpersons[5]])
    const removeLink = node.querySelectorAll(".person-selected-remove")[1]! as HTMLAnchorElement
    removeLink.click()
    tick()
    expect(node.querySelectorAll(".person-selected").length).toEqual(2)
    expect(component.selectedPersons).toEqual([testpersons[1], testpersons[5]])
  }))

  it ("fills form and sends data to service", fakeAsync( () => {
    const idBox: HTMLInputElement = node.querySelector(".exam-id")!
    idBox.value = "e-q1"
    idBox.dispatchEvent(new Event("input"))
    const lengthBox: HTMLInputElement = node.querySelector(".trial-length")!
    lengthBox.value = "35"
    lengthBox.dispatchEvent(new Event("input"))
    const personsSrc: HTMLSelectElement = node.querySelector(".persons-src")!
    personsSrc.options[2].selected = true
    personsSrc.options[4].selected = true
    personsSrc.dispatchEvent(new Event("change"))
    const selectButton: HTMLButtonElement = node.querySelector(".persons-select")!
    selectButton.click()
    /** TODO find a way to select dates by means of UI
    const periodBox: HTMLInputElement = node.querySelector(".period")!
    periodBox.value = "2023-01-20 10:15 to 2023-01-23 11:30"
    periodBox.dispatchEvent(new Event("input"))
    */
    const createButton: HTMLButtonElement = node.querySelector(".create")!
    component.period = {start: dayjs("2023-01-20T10:15:00Z"), end: dayjs("2023-01-23T11:30:00Z")}
    examsService.create.and.returnValue(Promise.resolve({}))
    createButton.click()
    expect(examsService.create).toHaveBeenCalledWith({id: "e-q1", quizId: "q1",
      start: new Date("2023-01-20T10:15:00Z"), end: new Date("2023-01-23T11:30:00Z"),
      trialLength: 35, testees: ["off3", "stud2"]})
    tick()
    expect(router.navigate).toHaveBeenCalledWith(["/exam"])
  }))
    
    
})

const testpersons = [
  {id: "off1", name: "off1 name", place: "Official"},
  {id: "off2", name: "off2 name", place: "Official"},
  {id: "off3", name: "off3 name", place: "Official"},
  {id: "stud1", name: "stud1 name", place: "Student"},
  {id: "stud2", name: "stud2 name", place: "Student"},
  {id: "stud3", name: "stud3 name", place: "Student"},
]

