import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing'
import { HttpHeaders } from "@angular/common/http"
import { TestBed } from '@angular/core/testing'
import { GlobalConfig } from "./global.config"
import { UiStore } from "./ui.store"
import { SessionQuery } from "./session/state/session.query"
import { SessionStore } from "./session/state/session.store"
import { PersonsState } from "./persons.state"

describe('PersonsState', () => {
  let state: PersonsState

  let controller: HttpTestingController

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        UiStore, SessionQuery, SessionStore, PersonsState,
        {provide: GlobalConfig, useValue: {baseApiUrl: "apiroot"}}
      ],
      imports: [ HttpClientTestingModule ]
    })

    state = TestBed.inject(PersonsState)
    controller = TestBed.inject(HttpTestingController)
    TestBed.inject(SessionStore).update({id: "user1", name: null})
  })

  afterEach(() => {
    controller.verify()
  })

  it ("loads person list", done => {
    state.init()
    const req = controller.expectOne("apiroot/persons")
    expect(req.request.method).toEqual("GET")
    const data = [
      {id: "off1", name: "off1 name", place: "Official"},
      {id: "stud1", name: "stud1 name", place: "Student"}
    ]
    req.flush(data)
    state.query.selectAll().subscribe(l => {
      expect(l).toEqual(data)
      done()
    })
  })

})

