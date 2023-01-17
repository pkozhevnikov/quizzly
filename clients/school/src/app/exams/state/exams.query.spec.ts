import { ExamsQuery } from './exams.query'
import { ExamsStore } from './exams.store'
import { Exam } from "./exam.model"

describe('ExamsQuery', () => {
  let query: ExamsQuery
  let store: ExamsStore

  beforeEach(() => {
    store = new ExamsStore()
    query = new ExamsQuery(store)
  })

  it('should create an instance', () => {
    expect(query).toBeTruthy()
  })
  
  it ("should relfect addition of new exam", done => {
    query.selectAll().subscribe(list => {
      if (list.length == 1) {
        expect(list[0].id).toEqual("dummy")
        done()
      }
    })
    store.add({id: "dummy"} as Exam)
  })

})
