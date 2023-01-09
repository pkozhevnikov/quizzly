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
  let node: HTMLElement

  let pword: HTMLInputElement
  let buttn: HTMLButtonElement

  beforeEach(async () => {
    TestBed.configureTestingModule({
      providers: [ 
        SessionService, UiQuery, SessionStore, UiStore, SessionQuery,
        {provide: ComponentFixtureAutoDetect, useValue: true}
      ],
      imports: [ HttpClientTestingModule, RouterTestingModule, FormsModule ],
      declarations: [ LoginComponent ]
    })
    .compileComponents().then(v => {
      fixture = TestBed.createComponent(LoginComponent)
      component = fixture.componentInstance
      node = fixture.nativeElement
      pword = node.querySelector("input.password")!
      buttn = node.querySelector("button[type=submit]")!
      router = TestBed.inject(Router)
      spyOn(router, "navigate")
    })
  })

  it('should create', () => {
    expect(component).toBeTruthy()
  })

  it("should show message on bad creds", () => {
    const name: HTMLInputElement = node.querySelector("input.username")!
    name.value = "notexists"
    name.dispatchEvent(new Event("input"))
    pword.value = "badpwd"
    pword.dispatchEvent(new Event("input"))
    fixture.detectChanges()
    console.log(name)
    console.log(`uname ${component.username}`)
    buttn.click()


    fixture.detectChanges()
    const notif = node.querySelector(".notif")!
    expect(notif.textContent).toBe("Wrong username or password")
    expect(notif.classList).toContain("error")
    
  })

  it("should react on login", () => {
    const uname: HTMLInputElement = node.querySelector("input.username")!
    uname.value = "off1"
    uname.dispatchEvent(new Event("input"))
    pword.value = "goodpwd"
    pword.dispatchEvent(new Event("input"))
    fixture.detectChanges()
    buttn.click()
    expect(router.navigate).toHaveBeenCalledWith(["/quizzes"])
    const notif = node.querySelector(".notif")!
    expect(notif.textContent).toBe("Successfully logged in")
    expect(notif.classList).toContain("info")
    
  })

})
