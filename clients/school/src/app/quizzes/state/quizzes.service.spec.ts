import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing'
import { HttpHeaders } from "@angular/common/http"
import { TestBed } from '@angular/core/testing'
import { QuizzesService } from './quizzes.service'
import { QuizzesQuery } from "./quizzes.query"
import { QuizzesStore } from './quizzes.store'
import { GlobalConfig } from "../../global.config"
import { UiStore } from "../../ui.store"
import { SessionQuery } from "../../session/state/session.query"
import { SessionStore } from "../../session/state/session.store"

describe('QuizzesService', () => {
  let quizzesService: QuizzesService
  let quizzesStore: QuizzesStore
  let quizzesQuery: QuizzesQuery
  let sessionStore: SessionStore
  let quizzesUpdate: jasmine.Spy

  let controller: HttpTestingController
  let uiStore: UiStore

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        QuizzesService, QuizzesStore, QuizzesQuery, UiStore, SessionQuery, SessionStore,
        {provide: GlobalConfig, useValue: {baseApiUrl: "apiroot"}}
      ],
      imports: [ HttpClientTestingModule ]
    })

    quizzesService = TestBed.inject(QuizzesService)
    quizzesStore = TestBed.inject(QuizzesStore)
    controller = TestBed.inject(HttpTestingController)
    uiStore = TestBed.inject(UiStore)
    sessionStore = TestBed.inject(SessionStore)
    sessionStore.update({id: "user1", name: null})
    quizzesQuery = TestBed.inject(QuizzesQuery)
    spyOn(quizzesStore, "set")
    quizzesUpdate = spyOn(quizzesStore, "update")
    spyOn(uiStore, "error")
    spyOn(uiStore, "info")
    spyOn(uiStore, "warn")
  })

  afterEach(() => {
    controller.verify()
  })

  it("provides quiz list", () => {
    quizzesService.get()
    const req = controller.expectOne("apiroot/quiz")
    expect(req.request.method).toEqual("GET")
    expect(req.request.headers).toEqual(new HttpHeaders().append("p", "user1"))
    req.flush(testlist)
    expect(quizzesStore.set).toHaveBeenCalledWith(testlist)
  })

  it("doesn't make publish request if in use", () => {
    spyOn(quizzesQuery, "getEntity").withArgs("used").and.returnValue(used)
    quizzesService.publish("used")
    controller.expectNone("apiroot/quiz/used")
    expect(uiStore.warn).toHaveBeenCalledWith("[used] is being used")
  })

  it("doesn't make publish request if is already published", () => {
    spyOn(quizzesQuery, "getEntity").withArgs("nowpublished").and.returnValue(nowpublished)
    quizzesService.publish("nowpublished")
    controller.expectNone("apiroot/quiz/nowpublished")
    expect(uiStore.warn).toHaveBeenCalledWith("[nowpublished] is already published")
  })

  it("doesn't make publish request if quiz not found", () => {
    spyOn(quizzesQuery, "getEntity").withArgs("notexists").and.returnValue(undefined)
    quizzesService.publish("notexists")
    controller.expectNone("apiroot/quiz/notexists")
    expect(uiStore.warn).toHaveBeenCalledWith("[notexists] not found")
  })

  it("updates store on publish", () => {
    quizzesUpdate.calls.reset()
    spyOn(quizzesQuery, "getEntity").withArgs("idle").and.returnValue(idle)
    quizzesService.publish("idle")
    const req = controller.expectOne("apiroot/quiz/idle")
    expect(req.request.method).toEqual("PATCH")
    req.flush("", {status: 204, statusText: ""})
    expect(quizzesUpdate.calls.argsFor(0)).toEqual(["idle", {isPublished: true, everPublished: true}])
  })

  it("doesn't make unpublish request if quiz not found", () => {
    spyOn(quizzesQuery, "getEntity").withArgs("notexists").and.returnValue(undefined)
    quizzesService.unpublish("notexists")
    controller.expectNone("apiroot/quiz/notexists")
    expect(uiStore.warn).toHaveBeenCalledWith("[notexists] not found")
  })

  it("doesn't make unpublish request if not published", () => {
    spyOn(quizzesQuery, "getEntity").withArgs("idle").and.returnValue(idle)
    quizzesService.unpublish("idle")
    controller.expectNone("apiroot/quiz/idle")
    expect(uiStore.warn).toHaveBeenCalledWith("[idle] is not published")
  })

  it("updates store on unpublish", () => {
    quizzesUpdate.calls.reset()
    spyOn(quizzesQuery, "getEntity").withArgs("nowpublished").and.returnValue(nowpublished)
    quizzesService.unpublish("nowpublished")
    const req = controller.expectOne("apiroot/quiz/nowpublished")
    expect(req.request.method).toEqual("DELETE")
    req.flush("", {status: 204, statusText: ""})
    expect(quizzesUpdate.calls.argsFor(0)).toEqual(["nowpublished", {isPublished: false}])
  })

})

export const idle = {
  id: "idle",
  title: "idle title",
  obsolete: false,
  inUse: false,
  isPublished: false,
  everPublished: false,
  recommendedTrialLength: 45
}

export const idleobsolete = {
  id: "idleobsolete",
  title: "idle obsolete title",
  obsolete: true,
  inUse: false,
  isPublished: false,
  everPublished: false,
  recommendedTrialLength: 45
}

export const used = {
  id: "used",
  title: "used title",
  obsolete: false,
  inUse: true,
  isPublished: false,
  everPublished: false,
  recommendedTrialLength: 45
}

export const nowpublished = {
  id: "nowpublished",
  title: "now published title",
  obsolete: false,
  inUse: false,
  isPublished: true,
  everPublished: true,
  recommendedTrialLength: 45
}

export const everpublished = {
  id: "everpublished",
  title: "ever published title",
  obsolete: false,
  inUse: false,
  isPublished: false,
  everPublished: true,
  recommendedTrialLength: 45
}

export const testlist = [ idle, idleobsolete, used, nowpublished, everpublished ]

