import { ComponentFixture, TestBed, ComponentFixtureAutoDetect } from '@angular/core/testing'
import { PersonsState } from "../../persons.state"
import { QuizzesQuery } from "../../quizzes/state/quizzes.query"
import { ExamsService } from "../state/exams.service"

import { of } from "rxjs"
import { matchers } from "../../util/matchers"

import { NewexamComponent } from './newexam.component'

describe('NewexamComponent', () => {
  let component: NewexamComponent
  let fixture: ComponentFixture<NewexamComponent>

  let quizzesQuery: jasmine.SpyObj<QuizzesQuery>
  let personsState: jasmine.SpyObj<PersonsState>
  let examsService: jasmine.SpyObj<ExamsService>
  let node: HTMLElement

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [ NewexamComponent ],
      providers: [
        {provide: ComponentFixtureAutoDetect, useValue: true},
        {provide: QuizzesQuery, useValue: jasmine.createSpyObj("QuizzesQuery", ["getEntity"])},
        {provide: PersonsState, useValue: jasmine.createSpyObj("PersonsState", ["selectAll"])},
        {provide: ExamsService, useValue: jasmine.createSpyObj("ExamsService", ["create"])},
      ],
    })
    .compileComponents()

    quizzesQuery = TestBed.inject(QuizzesQuery) as jasmine.SpyObj<QuizzesQuery>
    personsState = TestBed.inject(PersonsState) as jasmine.SpyObj<PersonsState>
    examsService = TestBed.inject(ExamsService) as jasmine.SpyObj<ExamsService>
    personsState.selectAll.and.returnValue(of(testpersons))
    quizzesQuery.getEntity.withArgs("q1").and.returnValue({
      id: "q1", title: "q1 title", obsolete: false, inUse: false, isPublished: false, everPublished: false
    })

    fixture = TestBed.createComponent(NewexamComponent)
    component = fixture.componentInstance
    node = fixture.nativeElement
  })

  it('should create', () => {
    expect(component).toBeTruthy()
  })

  xit ("requests quiz locally", () => {
    expect(quizzesQuery.getEntity).toHaveBeenCalledWith("q1")
    expect(node.querySelector(".quiz-id")).toHaveText("q1")
    expect(node.querySelector(".quiz-title")).toHaveText("q1 title")
  })

  xit ("loads person list", () => {
    expect(personsState.selectAll).toHaveBeenCalled()
    const personsSrc = node.querySelector(".persons-src")!
    expect(personsSrc.querySelectorAll(".person")).toHaveSize(testpersons.length)
    for (let i = 0; i < testpersons.length; i++) {
      expect(personsSrc.querySelectorAll(".person")[i]).toHaveText(testpersons[i].name)
    }
  })

  xit ("filters person list", () => {
    const filterBox: HTMLInputElement = node.querySelector(".persons-filter")!
    const personsSrc = node.querySelector(".persons-src")!
    expect(personsSrc.querySelectorAll(".person")).toHaveSize(testpersons.length)
    const filtered = testpersons.filter(p => p.id.startsWith("off"))
    filterBox.value = "off"
    filterBox.dispatchEvent(new Event("input"))
    expect(personsSrc.querySelectorAll(".person")).toHaveSize(3)
    for (let i = 0; i < 3; i++) {
      expect(personsSrc.querySelectorAll(".person")[i]).toHaveText(filtered[i].name)
    }
  })

  xit ("moves selected persons to selection and removes from selection", () => {
    const personsSrc: HTMLSelectElement = node.querySelector(".persons-src")!
    personsSrc.options[1].selected = true
    personsSrc.options[3].selected = true
    personsSrc.options[5].selected = true
    personsSrc.dispatchEvent(new Event("change"))
    const selectButton: HTMLButtonElement = node.querySelector(".persons-select")!
    selectButton.click()
    expect(node.querySelector(".person-selected")).toHaveSize(3)
    expect(component.selectedPersons).toEqual([testpersons[1], testpersons[3], testpersons[5]])
    const removeLink = node.querySelectorAll(".person-selected-remove")[1]! as HTMLAnchorElement
    removeLink.click()
    expect(component.selectedPersons).toEqual([testpersons[1], testpersons[5]])

  })

  xit ("fills form and sends data to service", () => {
    const lengthBox: HTMLInputElement = node.querySelector(".trial-length")!
    lengthBox.value = "35"
    lengthBox.dispatchEvent(new Event("input"))
    const personsSrc: HTMLSelectElement = node.querySelector(".persons-src")!
    personsSrc.options[2].selected = true
    personsSrc.options[4].selected = true
    personsSrc.dispatchEvent(new Event("change"))
    const periodBox: HTMLInputElement = node.querySelector(".period")!
    periodBox.value = "2023-01-20 10:15 ~ 2023-01-23 11:30"
    periodBox.dispatchEvent(new Event("input"))
    const createButton: HTMLButtonElement = node.querySelector(".create")!
    createButton.click()
    expect(examsService.create).toHaveBeenCalledWith({id: "e-q1", quizId: "q1",
      start: new Date("2023-01-20T10:15:00Z"), end: new Date("2023-01-23T11:30:00Z"),
      trialLength: 35, testees: ["off3", "stud2"]})
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

