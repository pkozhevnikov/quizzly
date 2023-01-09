import { HttpClientTestingModule } from '@angular/common/http/testing'
import { TestBed } from '@angular/core/testing'
import { SessionService } from './session.service'
import { SessionStore } from './session.store'
import { UiStore } from "../../ui.store"

describe('SessionService', () => {
  let sessionService: SessionService
  let sessionStore: SessionStore
  let uiStore: UiStore

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [SessionService, SessionStore, UiStore],
      imports: [ HttpClientTestingModule ]
    })

    sessionService = TestBed.inject(SessionService)
    sessionStore = TestBed.inject(SessionStore)
    uiStore = TestBed.inject(UiStore)
    spyOn(uiStore, "error")
    spyOn(uiStore, "info")
    spyOn(sessionStore, "update")
  })

  it('should be created', () => {
    expect(sessionService).toBeDefined()
  })

  it("should not login", () => {
    expect(sessionService.login("notexist", "")).toBeFalse()
    expect(uiStore.error).toHaveBeenCalledWith("Wrong username or password")
  })

  it("should login", () => {
    expect(sessionService.login("off1", "")).toBeTrue()
    expect(uiStore.info).toHaveBeenCalledWith("Successfully logged in")
    expect(sessionStore.update).toHaveBeenCalledWith({id: "off1", name: "off1 name"})
  })

})
