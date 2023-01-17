import { QuizzesQuery } from './quizzes.query'
import { QuizzesStore } from './quizzes.store'
import { Quiz } from "./quiz.model"

describe('QuizzesQuery', () => {
  let query: QuizzesQuery
  let store: QuizzesStore

  beforeEach(() => {
    store = new QuizzesStore()
    query = new QuizzesQuery(store)
  })

  it('should create an instance', () => {
    expect(query).toBeTruthy()
  })

})
