import { flushMicrotasks, tick, fakeAsync, ComponentFixture, 
            TestBed, ComponentFixtureAutoDetect } from '@angular/core/testing'

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
      declarations: [ QuizlistComponent ]
    }).compileComponents()
    
    quizzesService = TestBed.inject(QuizzesService) as jasmine.SpyObj<QuizzesService>
    quizzesQuery = TestBed.inject(QuizzesQuery) as jasmine.SpyObj<QuizzesQuery>
    quizzesQuery.selectAll.and.returnValue(of(testlist))
  
    fixture = TestBed.createComponent(QuizlistComponent)
    component = fixture.componentInstance
    node = fixture.nativeElement
    fixture.detectChanges()
  })

  it("should show quiz list", () => { 
    expect(quizzesService.get).toHaveBeenCalled()
    expect(quizzesQuery.selectAll).toHaveBeenCalled()
    const rows = node.querySelectorAll("table.quiz-list tr.quiz")
    expect(rows.length).toEqual(5)
    const idleRow = rows[0]
    const idleCols = idleRow.querySelectorAll("td")
    expect(idleCols[0].textContent).toEqual("idle")
    expect(idleCols[1].textContent).toEqual("idle title")
    expect(idleCols[2].textContent).toEqual("")
    expect(idleCols[3].textContent).toEqual("")
    expect(idleCols[4].textContent).toEqual("")
    expect(idleCols[5].textContent).toEqual("")
    
  })

})
