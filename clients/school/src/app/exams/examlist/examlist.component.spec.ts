import { tick, fakeAsync, ComponentFixture, TestBed, ComponentFixtureAutoDetect } from '@angular/core/testing'
import { RouterTestingModule } from "@angular/router/testing"
import { formatDate, DATE_PIPE_DEFAULT_TIMEZONE } from "@angular/common" 
import { ExamsQuery } from "../../exams/state/exams.query" 
import { ExamsService } from "../../exams/state/exams.service" 
import { ExamlistComponent } from './examlist.component' 
import { testexams, examPending, examUpcoming, 
      examInProgress, examEnded, examCancelled } from "../state/exams.service.spec" 
import { of } from "rxjs" 

describe('ExamlistComponent', () => { 
  let component: ExamlistComponent 
  let fixture: ComponentFixture<ExamlistComponent>
  let examsService: jasmine.SpyObj<ExamsService>
  let examsQuery: jasmine.SpyObj<ExamsQuery>
  let node: HTMLElement

  beforeEach(async () => {
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

  it('should create', () => {
    expect(component).toBeTruthy()
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
      expect(row.querySelector("td.exam-id")!.textContent).toEqual(exam.id)
      expect(row.querySelector("td.exam-quiz-id")!.textContent).toEqual(exam.quiz.id)
      expect(row.querySelector("td.exam-quiz-title")!.textContent).toEqual(exam.quiz.title)
      expect(row.querySelector("td.exam-host")!.textContent).toEqual(exam.host.name)
      expect(row.querySelector("td.exam-state")!.textContent).toEqual(exam.state)
      const start = formatDate(exam.period.start, "MMMM d, y, HH:mm", "en-US", "UTC")
      const end = formatDate(exam.period.end, "MMMM d, y, HH:mm", "en-US", "UTC")
      expect(row.querySelector("td.exam-start")!.textContent).toEqual(start)
      expect(row.querySelector("td.exam-end")!.textContent).toEqual(end)
      expect(row.querySelector("td.exam-length")!.textContent).toEqual(exam.trialLength + "")
      if (exam.state == "Pending" || exam.state == "Upcoming") {
        const cancel = row.querySelector("a.exam-cancel")! as HTMLAnchorElement
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
