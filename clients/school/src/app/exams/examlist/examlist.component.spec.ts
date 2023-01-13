import { ComponentFixture, TestBed, ComponentFixtureAutoDetect } from '@angular/core/testing'
import { RouterTestingModule } from "@angular/router/testing"
import { formatDate, DATE_PIPE_DEFAULT_TIMEZONE } from "@angular/common" 
import { ExamsQuery } from "../../exams/state/exams.query" 
import { ExamsService } from "../../exams/state/exams.service" 
import { ExamlistComponent } from './examlist.component' 
import { testexams } from "../state/exams.service.spec" 
import { of } from "rxjs" 

const matchers: jasmine.CustomMatcherFactories = {
  toHaveText: (util: jasmine.MatchersUtil) => {
    return {
      compare: function(actual: Element | null, expected: any) {
        const result = {pass: false, message: ""}
        if (actual instanceof HTMLElement) {
          result.pass = util.equals(actual.textContent, expected)
          result.message = result.pass ?
            `Expected element to contain text '${expected}'` :
            `Expected element to contain text '${expected}' but it was '${actual.textContent}'`
        } else {
          result.pass = false
          result.message = "Tested object should be a Element"
        }
        return result
      }
    }
  }
}

describe('ExamlistComponent', () => { 
  let component: ExamlistComponent 
  let fixture: ComponentFixture<ExamlistComponent>
  let examsService: jasmine.SpyObj<ExamsService>
  let examsQuery: jasmine.SpyObj<ExamsQuery>
  let node: HTMLElement

  beforeEach(async () => {

    jasmine.addMatchers(matchers)

    await TestBed.configureTestingModule({
      providers: [
        {provide: ExamsService, useValue: jasmine.createSpyObj("ExamsService", ["get", "cancel"])},
        {provide: ExamsQuery, useValue: jasmine.createSpyObj("ExamsQuery", ["selectAll"])},
        {provide: ComponentFixtureAutoDetect, useValue: true},
        {provide: DATE_PIPE_DEFAULT_TIMEZONE, useValue: "UTC"},
      ],
      imports: [ RouterTestingModule ],
      declarations: [ ExamlistComponent ]
    })
    .compileComponents()

    examsService = TestBed.inject(ExamsService) as jasmine.SpyObj<ExamsService>
    examsQuery = TestBed.inject(ExamsQuery) as jasmine.SpyObj<ExamsQuery>
    examsQuery.selectAll.and.returnValue(of(testexams))

    fixture = TestBed.createComponent(ExamlistComponent)
    component = fixture.componentInstance
    node = fixture.nativeElement

  })

  it ("should load and show list", () => {
    expect(examsService.get).toHaveBeenCalled()
    expect(examsQuery.selectAll).toHaveBeenCalled()
    expect(node.querySelectorAll("table.exam-list tr.exam")).toHaveSize(5)
  })

  for (let i = 0; i < testexams.length; i++) {
    const exam = testexams[i]
    it (`should show row correctly: ${exam.id}`, () => {
      const row = node.querySelectorAll("table.exam-list tr.exam")[i]!
      expect(row.querySelector("td.exam-id")).toHaveText(exam.id)
      expect(row.querySelector("td.exam-quiz-id")).toHaveText(exam.quiz.id)
      expect(row.querySelector("td.exam-quiz-title")).toHaveText(exam.quiz.title)
      expect(row.querySelector("td.exam-host")).toHaveText(exam.host.name)
      expect(row.querySelector("td.exam-state")).toHaveText(exam.state)
      const start = formatDate(exam.period.start, "MMMM d, y, HH:mm", "en-US", "UTC")
      const end = formatDate(exam.period.end, "MMMM d, y, HH:mm", "en-US", "UTC")
      expect(row.querySelector("td.exam-start")).toHaveText(start)
      expect(row.querySelector("td.exam-end")).toHaveText(end)
      expect(row.querySelector("td.exam-length")).toHaveText(exam.trialLength + "")
      if (exam.state == "Pending" || exam.state == "Upcoming") {
        const cancel: HTMLAnchorElement = row.querySelector("a.exam-cancel")! 
        cancel.click()
        expect(examsService.cancel).toHaveBeenCalledWith(exam.id)
      }
      if (exam.state == "Pending") {
        const edit = row.querySelector("a.exam-edit")!
        expect(edit.getAttribute("href")).toEqual(`/exam/${exam.id}`)
      }
      if (exam.state == "InProgress" || exam.state == "Ended" || exam.state == "Cancelled") {
        expect(row.querySelectorAll("td.exam-actions a")).toHaveSize(0)
      }
    })
  }

})
