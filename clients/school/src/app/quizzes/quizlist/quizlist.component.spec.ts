import { flushMicrotasks, tick, fakeAsync, ComponentFixture, 
            TestBed, ComponentFixtureAutoDetect } from '@angular/core/testing'
import { RouterTestingModule } from "@angular/router/testing"

import { QuizlistComponent } from './quizlist.component'
import { QuizzesQuery } from "../state/quizzes.query"
import { QuizzesService } from "../state/quizzes.service"
import { Quiz } from "../state/quiz.model"
import { idle, idleobsolete, used, nowpublished, everpublished, 
            testlist } from "../state/quizzes.service.spec"
import { Observable, of, from } from "rxjs"

describe('QuizlistComponent', () => {
  let component: QuizlistComponent
  let fixture: ComponentFixture<QuizlistComponent>
  let node: HTMLElement

  let quizzesService: jasmine.SpyObj<QuizzesService>
  let quizzesQuery: jasmine.SpyObj<QuizzesQuery>


  beforeEach(async () => {

    await TestBed.configureTestingModule( {
      providers: [
        {provide: QuizzesQuery, useValue: jasmine.createSpyObj("QuizzesQuery", 
          ["getEntity", "selectAll"])},
        {provide: QuizzesService, useValue: jasmine.createSpyObj("QuizzesService",
          ["get", "publish", "unpublish"])},
        {provide: ComponentFixtureAutoDetect, useValue: true}
      ],
      declarations: [ QuizlistComponent ],
      imports: [ RouterTestingModule ]
    }).compileComponents()
    
    quizzesService = TestBed.inject(QuizzesService) as jasmine.SpyObj<QuizzesService>
    quizzesQuery = TestBed.inject(QuizzesQuery) as jasmine.SpyObj<QuizzesQuery>
    quizzesQuery.selectAll.and.returnValue(of(testlist))
  
    fixture = TestBed.createComponent(QuizlistComponent)
    component = fixture.componentInstance
    node = fixture.nativeElement
    fixture.detectChanges()
  })

  it ("should show correct number of quizzes", () => {
    expect(quizzesService.get).toHaveBeenCalled()
    expect(quizzesQuery.selectAll).toHaveBeenCalled()
    expect(node.querySelectorAll("table.quiz-list tr.quiz").length).toEqual(5)
  })

  type Alist = NodeListOf<HTMLAnchorElement>
  type CheckLinks = (ls: Alist) => void
  const listParams = [
    ["idle row", "idle", "idle title", "notchecked", "notchecked", "notchecked", "notchecked", 
    (links: Alist) => {
      expect(links.length).toEqual(2)
      expect(links[0]).toHaveClass("toexam")
      expect(links[0].getAttribute("href")).toEqual("/newexam/idle")
      expect(links[1]).toHaveClass("publish")
    }],
    ["idle obsolete row", "idleobsolete", "idle obsolete title", "checked", "notchecked", "notchecked",
      "notchecked", (links: Alist) => {
      expect(links.length).toEqual(1)
      expect(links[0]).toHaveClass("publish")
    }],
    ["used row", "used", "used title", "notchecked", "checked", "notchecked", "notchecked",
    (links: Alist) => {
      expect(links.length).toEqual(1)
      expect(links[0]).toHaveClass("toexam")
      expect(links[0].getAttribute("href")).toEqual("/newexam/used")
    }],
    ["now published row", "nowpublished", "now published title", "notchecked", "notchecked",
      "checked", "checked", (links: Alist) => {
      expect(links.length).toEqual(1)
      expect(links[0]).toHaveClass("unpublish")
    }],
    ["ever published row", "everpublished", "ever published title", "notchecked", "notchecked",
      "notchecked", "checked", (links: Alist) => {
      expect(links.length).toEqual(1)
      expect(links[0]).toHaveClass("publish")
    }]
  ]

  for (let i = 0; i < listParams.length; i++) {
    const params = listParams[i]
    it (`should show row correctly: ${params[0]}`, () => {
      const tds = node.querySelectorAll("table.quiz-list tr.quiz")[i].querySelectorAll("td")
      expect(tds[0].textContent).toEqual(params[1] as string)
      expect(tds[1].textContent).toEqual(params[2] as string)
      expect(tds[2]).toHaveClass(params[3] as string)
      expect(tds[3]).toHaveClass(params[4] as string)
      expect(tds[4]).toHaveClass(params[5] as string)
      expect(tds[5]).toHaveClass(params[6] as string)
      const cl = params[7] as CheckLinks
      cl(tds[6].querySelectorAll("a"))
    })
  }
  
})
