import { ComponentFixture, TestBed, ComponentFixtureAutoDetect } from '@angular/core/testing'
import { FormsModule, ReactiveFormsModule } from "@angular/forms"

import { PersonsState } from "../../persons.state"
import { matchers } from "../../util/matchers"
import { of } from "rxjs"

import { PersonchooserComponent } from './personchooser.component'

describe('PersonchooserComponent', () => {
  let component: PersonchooserComponent
  let fixture: ComponentFixture<PersonchooserComponent>

  let personsState: jasmine.SpyObj<PersonsState>
  let node: HTMLElement

  beforeEach(async () => {
    jasmine.addMatchers(matchers)
    await TestBed.configureTestingModule({
      declarations: [ PersonchooserComponent ],
      providers: [
        {provide: ComponentFixtureAutoDetect, useValue: true},
        {provide: PersonsState, useValue: jasmine.createSpyObj("PersonsState", ["selectAll"])},
      ],
      imports: [ FormsModule, ReactiveFormsModule ]
    })
    .compileComponents()

    personsState = TestBed.inject(PersonsState) as jasmine.SpyObj<PersonsState>
    personsState.selectAll.withArgs("").and.returnValue(of(testpersons))

    fixture = TestBed.createComponent(PersonchooserComponent)
    component = fixture.componentInstance
    node = fixture.nativeElement
  })

  it ("loads person list", () => {
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
    expect(personsSrc.querySelectorAll(".person")).toHaveSize(testpersons.length)
    const filtered = testpersons.filter(p => p.id.startsWith("off"))
    personsState.selectAll.withArgs("off").and.returnValue(of(testpersons.slice(0, 3)))
    filterBox.value = "off"
    filterBox.dispatchEvent(new Event("input"))
    expect(personsSrc.querySelectorAll(".person")!.length).toEqual(3)
    for (let i = 0; i < 3; i++) {
      expect(personsSrc.querySelectorAll(".person")[i]).toHaveText(filtered[i].name)
    }
  })

  it ("moves selected persons to selection and removes from selection", () => {
    const personsSrc: HTMLSelectElement = node.querySelector(".persons-src")!
    personsSrc.options[1].selected = true
    personsSrc.options[3].selected = true
    personsSrc.options[5].selected = true
    personsSrc.dispatchEvent(new Event("change"))
    const selectButton: HTMLButtonElement = node.querySelector(".persons-select")!
    selectButton.click()
    expect(node.querySelectorAll(".person-selected").length).toEqual(3)
    expect(component.chosen).toEqual([testpersons[1], testpersons[3], testpersons[5]])
    const removeLink = node.querySelectorAll(".person-selected-remove")[1]! as HTMLAnchorElement
    removeLink.click()
    expect(component.chosen).toEqual([testpersons[1], testpersons[5]])
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

