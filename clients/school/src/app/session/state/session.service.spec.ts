import { HttpClientTestingModule } from '@angular/common/http/testing'
import { RouterTestingModule } from "@angular/router/testing"
import { Router } from "@angular/router"
import { TestBed } from '@angular/core/testing'
import { SessionService } from './session.service'
import { SessionStore } from './session.store'
import { UiStore } from "../../ui.store"

describe('SessionService', () => {
  let sessionService: SessionService
  let sessionStore: SessionStore
  let uiStore: UiStore
  let router: Router

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [SessionService, SessionStore, UiStore],
      imports: [ HttpClientTestingModule, RouterTestingModule ]
    })

    sessionService = TestBed.inject(SessionService)
    sessionStore = TestBed.inject(SessionStore)
    uiStore = TestBed.inject(UiStore)
    router = TestBed.inject(Router)
    spyOn(router, "navigate")
    spyOn(uiStore, "error")
    spyOn(uiStore, "info")
    spyOn(sessionStore, "update")
  })

  it('should be created', () => {
    expect(sessionService).toBeDefined()
  })

  it("should not login", () => {
    sessionService.login("notexist", "")
    expect(uiStore.error).toHaveBeenCalledWith("Wrong username or password")
  })

  it("should login", () => {
    sessionService.login("off1", "")
    expect(uiStore.info).toHaveBeenCalledWith("Successfully logged in")
    expect(sessionStore.update).toHaveBeenCalledWith({id: "off1", name: "off1 name"})
  })

  it("should logout", () => {
    sessionService.logout()
    expect(sessionStore.update).toHaveBeenCalledWith({id: null, name: null})
    expect(router.navigate).toHaveBeenCalledWith(["/login"])
  })

})
