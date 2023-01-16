import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing'
import { TestBed } from '@angular/core/testing'

import { HttpHeaders } from "@angular/common/http"

import { HttpBasedService } from "./httpbased.service"
import { UiStore } from "../ui.store"
import { UiQuery } from "../ui.query"
import { HttpClient } from "@angular/common/http"
import { SessionQuery } from "../session/state/session.query"
import { SessionStore } from "../session/state/session.store"

const GET = "GET"
const DELETE = "DELETE"
const POST = "POST"
const PUT = "PUT"
const PATCH = "PATCH"

describe('HttpBasedService', () => {
  let service: HttpBasedService
  let sessionStore: SessionStore
  let uiStore: UiStore
  let controller: HttpTestingController

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [ UiStore, UiQuery, HttpClient, SessionQuery, SessionStore ],
      imports: [ HttpClientTestingModule ]
    })

    controller = TestBed.inject(HttpTestingController)
    sessionStore = TestBed.inject(SessionStore)
    uiStore = TestBed.inject(UiStore)
    spyOn(uiStore, "warn")
    spyOn(uiStore, "error")
    spyOn(uiStore, "info")

    service = new HttpBasedService(
      "apiroot",
      TestBed.inject(HttpClient),
      TestBed.inject(SessionQuery),
      uiStore
    )

  })

  afterEach(() => {
    controller.verify()
  })

  it("should not make request if not logged in", () => {
    service.request(GET, "quiz", {})
    controller.expectNone("apiroot/quiz")
    expect(uiStore.error).toHaveBeenCalledWith("Not logged in")
  })

  it("processes 401", () => {
    sessionStore.update({id: "user1", name: "user1 name"})
    service.request(GET, "quiz", {})
    const req = controller.expectOne("apiroot/quiz")
    expect(req.request.method).toEqual(GET)
    req.flush("", {status: 401, statusText: ""})
    expect(uiStore.error).toHaveBeenCalledWith("Access denied")
  })

  it("processes 422", () => {
    sessionStore.update({id: "user2", name: ""})
    service.request(DELETE, "test2", {})
    const req = controller.expectOne("apiroot/test2")
    expect(req.request.method).toEqual(DELETE)
    expect(req.request.headers).toEqual(new HttpHeaders().append("p", "user2"))
    req.flush({reason: {code: 456, phrase: "invalid data"}, clues: ["clue1", "clue2"]}, 
      {status: 422, statusText: ""})
    expect(uiStore.error).toHaveBeenCalledWith('(456) invalid data: ["clue1","clue2"]')
  })

  it("processes 200", done => {
    sessionStore.update({id: "user3", name: ""})
    let resultProc: any
    const resp = new Promise((res, rej) => resultProc = res)
    service.request(GET, "test3", {200: v => {
      uiStore.warn(v)
      resultProc(v)
    }})
    const req = controller.expectOne("apiroot/test3")
    expect(req.request.method).toEqual(GET)
    expect(req.request.headers).toEqual(new HttpHeaders().append("p", "user3"))
    req.flush("hello world")
    expect(uiStore.warn).toHaveBeenCalledWith("hello world")
    expect(uiStore.error).not.toHaveBeenCalled()
    resp.then(v => {
      expect(v).toEqual("hello world")
      done()
    })
  })

  it("processes status dependent function", () => {
    sessionStore.update({id: "user4", name: ""})
    service.request(DELETE, "test4", {204: _ => uiStore.info("Item deleted")})
    const req = controller.expectOne("apiroot/test4")
    expect(req.request.method).toEqual(DELETE)
    expect(req.request.headers).toEqual(new HttpHeaders().append("p", "user4"))
    req.flush({}, {status: 204, statusText: ""})
    expect(uiStore.info).toHaveBeenCalledWith("Item deleted")
  })

  it("passes request body", () => {
    sessionStore.update({id: "user5", name: ""})
    service.request(PUT, "test5", {204: _ => uiStore.info("processed")}, {some: "body"})
    const req = controller.expectOne("apiroot/test5")
    expect(req.request.method).toEqual(PUT)
    expect(req.request.headers).toEqual(new HttpHeaders().append("p", "user5"))
    expect(req.request.body).toEqual({some:"body"})
    req.flush({}, {status: 204, statusText: ""})
    expect(uiStore.info).toHaveBeenCalledWith("processed")
  })

  it("notifies of unknown good status", () => {
    sessionStore.update({id: "user6", name: null})
    service.request(GET, "test6", {})
    const req = controller.expectOne("apiroot/test6")
    expect(req.request.method).toEqual(GET)
    req.flush({}, {status: 202, statusText: ""})
    expect(uiStore.warn).toHaveBeenCalledWith("action for GET test6 202 not specified")
  })

  it("notifies of unknow error status", () => {
    sessionStore.update({id: "user7", name: null})
    service.request(GET, "test7", {})
    const req = controller.expectOne("apiroot/test7")
    req.flush({}, {status: 400, statusText: "not found"})
    expect(uiStore.error)
      .toHaveBeenCalledWith("Http failure response for apiroot/test7: 400 not found")
  })

})
