import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing'
import { HttpHeaders } from "@angular/common/http"
import { TestBed } from '@angular/core/testing'
import { ExamsService } from './exams.service'
import { ExamsStore } from './exams.store'
import { UiStore } from "../../ui.store"
import { GlobalConfig } from "../../global.config"
import { SessionStore } from "../../session/state/session.store"
import { SessionQuery } from "../../session/state/session.query"
import { QuizzesQuery } from "../../quizzes/state/quizzes.query"
import { QuizzesStore } from "../../quizzes/state/quizzes.store"
import { DONE } from "../../util/httpbased.service"

describe('ExamsService', () => {
  let examsService: ExamsService
  let examsStore: ExamsStore

  let examsUpdate: jasmine.Spy

  let controller: HttpTestingController
  let uiStore: UiStore
  let quizzesQuery: jasmine.SpyObj<QuizzesQuery>
  let quizzesStore: jasmine.SpyObj<QuizzesStore>

  beforeEach (() => {
    TestBed.configureTestingModule({
      providers: [
        ExamsService, ExamsStore, UiStore, SessionQuery, SessionStore,
        {provide: GlobalConfig, useValue: {baseApiUrl: "apiroot"}},
        {provide: QuizzesQuery, useValue: jasmine.createSpyObj("QuizzesQuery", ["getEntity"])},
        {provide: QuizzesStore, useValue: jasmine.createSpyObj("QuizzesStore", ["update"])},
      ],
      imports: [ HttpClientTestingModule ]
    })

    examsService = TestBed.inject(ExamsService)
    examsStore = TestBed.inject(ExamsStore)
    controller = TestBed.inject(HttpTestingController)
    quizzesQuery = TestBed.inject(QuizzesQuery) as jasmine.SpyObj<QuizzesQuery>
    quizzesStore = TestBed.inject(QuizzesStore) as jasmine.SpyObj<QuizzesStore>
    TestBed.inject(SessionStore).update({id: "off1", name: "off1 name"})
    uiStore = TestBed.inject(UiStore)
    spyOn(uiStore, "info")
    spyOn(examsStore, "set")
    examsUpdate = spyOn(examsStore, "update")
    spyOn(examsStore, "add")
  })

  afterEach (() => {
    controller.verify()
  })

  it ("should load exam list", () => {
    examsService.get()
    const req = controller.expectOne("apiroot/exam")
    expect(req.request.method).toEqual("GET")
    expect(req.request.headers).toEqual(new HttpHeaders().append("p", "off1"))
    req.flush(testexams)
    expect(examsStore.set).toHaveBeenCalledWith(testexams)
  })

  it ("should create an exam", done => {
    quizzesQuery.getEntity.withArgs("q1").and.returnValue({
      id: "q1", title: "q1 title", obsolete: false, inUse: false, isPublished: false,
        everPublished: false, recommendedTrialLength: 45})
    const createReq = {
      id: "newexam",
      quizId: "q1",
      start: new Date("2023-01-20T12:00:00Z"),
      end: new Date("2023-01-22T12:00:00Z"),
      trialLength: 75,
      testees: ["off2", "stud1", "stud2"]
    }
    const resp = examsService.create(createReq)
    const req = controller.expectOne("apiroot/exam")
    expect(req.request.method).toEqual("POST")
    expect(req.request.body).toEqual(createReq)
    const createDetails = {preparationStart: new Date("2023-01-19T12:00:00Z"), 
      host: {id: "off1", name: "off1 name", place: "Official"}}
    req.flush(createDetails)
    expect(examsStore.add).toHaveBeenCalledWith({
      id: "newexam", 
      quiz: {id: "q1", title: "q1 title"}, 
      period: {start: createReq.start, end: createReq.end}, 
      host: createDetails.host,
      state: "Pending",
      trialLength: 75,
      prestartAt: createDetails.preparationStart
    })
    expect(quizzesStore.update.calls.argsFor(0) as any[]).toEqual(["q1", {inUse: true}])
    resp.then(r => {
      expect(r).toEqual(DONE)
      done()
    })
  })

  it ("should cancel exam", () => {
    examsUpdate.calls.reset()
    examsService.cancel("upcoming")
    const req = controller.expectOne("apiroot/exam/upcoming")
    expect(req.request.method).toEqual("DELETE")
    req.flush("", {status: 204, statusText: ""})
    expect(examsUpdate.calls.argsFor(0) as any[]).toEqual(["upcoming", {state: "Cancelled"}])
  })

  it ("should change trial length", () => {
    examsUpdate.calls.reset()
    examsService.changeTrialLength("pending", 180)
    const req = controller.expectOne("apiroot/exam/pending")
    expect(req.request.method).toEqual("POST")
    expect(req.request.body).toEqual({length: 180})
    req.flush("", {status: 204, statusText: ""})
    expect(examsUpdate.calls.argsFor(0) as any[]).toEqual(["pending", {trialLength: 180}])
  })

  it ("should include testees", done => {
    const resp = examsService.includeTestees("pending", ["stud4", "stud5"])
    const req = controller.expectOne("apiroot/exam/pending")
    expect(req.request.method).toEqual("PUT")
    expect(req.request.body).toEqual(["stud4", "stud5"])
    const included = [{id: "stud5", name: "stud5 name", place: "Student"}]
    req.flush(included)
    expect(uiStore.info).toHaveBeenCalledWith("Testees included: stud5 name")
    resp.then(res => {
      expect(res).toEqual(included)
      done()
    })
  })

  it ("should exclude testees", done => {
    const resp = examsService.excludeTestees("pending", ["stud2", "stud3"])
    const req = controller.expectOne("apiroot/exam/pending")
    expect(req.request.method).toEqual("PATCH")
    expect(req.request.body).toEqual(["stud2", "stud3"])
    const excluded = [{id: "stud2", name: "stud2 name", place: "Student"}]
    req.flush(excluded)
    expect(uiStore.info).toHaveBeenCalledWith("Testees excluded: stud2 name")
    resp.then(res => {
      expect(res).toEqual(excluded)
      done()
    })
  })

  it ("should retrieve exam testees", done => {
    const persons = [
      {id: "off1", name: "off1 name", place: "Official"},
      {id: "stud1", name: "stud1 name", place: "Student"}
    ]
    examsService.getTestees("pending").then(l => {
      expect(l).toEqual(persons)
      done()
    })
    const req = controller.expectOne("apiroot/exam/pending")
    expect(req.request.method).toEqual("GET")
    req.flush(persons)
  })

})

export const examPending = {
  id: "pending",
  quiz: {id: "q1", title: "q1 title"},
  period: {start: new Date("2023-01-10T10:00:00Z"), end: new Date("2023-01-11T10:00:00Z")},
  host: {id: "off1", name: "off1 name", place: "Official"},
  state: "Pending",
  trialLength: 45,
  prestartAt: new Date("2023-01-09T10:00:00Z")
}

export const examUpcoming = {
  id: "upcoming",
  quiz: {id: "q2", title: "q2 title"},
  period: {start: new Date("2023-01-15T12:30:00Z"), end: new Date("2023-01-17T12:30:00Z")},
  host: {id: "off2", name: "off2 name", place: "Official"},
  state: "Upcoming",
  trialLength: 60,
  prestartAt: new Date("2023-01-13T12:30:00Z")
}

export const examInProgress = {
  id: "inprogress",
  quiz: {id: "q3", title: "q3 title"},
  period: {start: new Date("2023-01-12T09:20:00Z"), end: new Date("2023-01-14T09:20:00Z")},
  host: {id: "off3", name: "off3 name", place: "Official"},
  state: "InProgress",
  trialLength: 30,
  prestartAt: new Date("2023-01-10T09:20:00Z")
}

export const examEnded = {
  id: "ended",
  quiz: {id: "q4", title: "q4 title"},
  period: {start: new Date("2023-01-08T09:00:00Z"), end: new Date("2023-01-09T09:00:00Z")},
  host: {id: "off1", name: "off1 name", place: "Official"},
  state: "Ended",
  trialLength: 90,
  prestartAt: new Date("2023-01-07T09:00:00Z")
}

export const examCancelled = {
  id: "cancelled",
  quiz: {id: "q5", title: "q5 title"},
  period: {start: new Date("2023-01-06T15:00:00Z"), end: new Date("2023-01-08T15:00:00Z")},
  host: {id: "off3", name: "off3 name", place: "Official"},
  state: "Cancelled",
  cancelledAt: new Date("2023-01-06T10:11:12Z"),
  trialLength: 50,
  prestartAt: new Date("2023-01-04T15:00:00Z")
}

export const testexams = [
  examPending,
  examUpcoming,
  examInProgress,
  examEnded,
  examCancelled
]



