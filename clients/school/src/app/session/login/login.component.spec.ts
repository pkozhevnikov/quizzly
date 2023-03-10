import { HttpClientTestingModule } from '@angular/common/http/testing'
import { RouterTestingModule } from "@angular/router/testing"
import { ComponentFixture, TestBed, ComponentFixtureAutoDetect } from '@angular/core/testing'
import { Router } from "@angular/router"
import { By } from "@angular/platform-browser"
import { DebugElement } from "@angular/core"
import { FormsModule, ReactiveFormsModule } from "@angular/forms"

import { LoginComponent } from './login.component'
import { SessionService } from "../state/session.service"
import { SessionStore } from "../state/session.store"
import { SessionQuery } from "../state/session.query"
import { UiStore } from "../../ui.store"
import { UiQuery } from "../../ui.query"

describe('LoginComponent', () => {
  let component: LoginComponent
  let fixture: ComponentFixture<LoginComponent>
  let router: Router
  let uiStore: UiStore
  let node: HTMLElement

  let uname: HTMLInputElement
  let pword: HTMLInputElement
  let buttn: HTMLElement

  beforeEach(async () => {
    TestBed.configureTestingModule({
      providers: [ 
        SessionService, UiQuery, SessionStore, UiStore, SessionQuery,
        {provide: ComponentFixtureAutoDetect, useValue: true}
      ],
      imports: [ HttpClientTestingModule, RouterTestingModule, FormsModule, ReactiveFormsModule ],
      declarations: [ LoginComponent ]
    })
    .compileComponents().then(v => {
      fixture = TestBed.createComponent(LoginComponent)
      component = fixture.componentInstance
      node = fixture.nativeElement
      uname = node.querySelector("input.username")!
      pword = node.querySelector("input.password")!
      buttn = node.querySelector(".try-login")!
      router = TestBed.inject(Router)
      spyOn(router, "navigate")
      uiStore = TestBed.inject(UiStore)
      spyOn(uiStore, "error")
      spyOn(uiStore, "info")
    })
  })

  it('should create', () => {
    expect(component).toBeTruthy()
  })

  it("should show message on bad creds", () => {
    uname.value = "notexists"
    pword.value = "badpwd"
    uname.dispatchEvent(new Event("input"))
    pword.dispatchEvent(new Event("input"))
    buttn.click()

    expect(uiStore.error).toHaveBeenCalledWith("Wrong username or password")

  })

  it("should react on login", () => {
    uname.value = "off1"
    pword.value = "goodpwd"
    uname.dispatchEvent(new Event("input"))
    pword.dispatchEvent(new Event("input"))
    buttn.click()

    expect(router.navigate).toHaveBeenCalledWith(["/quizzes"])
    expect(uiStore.info).toHaveBeenCalledWith("Successfully logged in")
    
  })

})
