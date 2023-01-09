import { UiQuery } from './ui.query'
import { UiStore, Notif } from './ui.store'

describe('UiQuery', () => {
  let store: UiStore
  let query: UiQuery

  beforeEach(() => {
    store = new UiStore()
    query = new UiQuery(store)
  })

  it('should create an instance', () => {
    expect(query).toBeTruthy()
  })

  it("should provide updated notif", done => {
    expect(query.getValue().notif).toBe(Notif.EMPTY)
    store.warn("hello warn")
    query.notif$.subscribe(n => {
      expect(n.text).withContext("check text").toBe("hello warn")
      expect(n.kind).withContext("check kind").toBe("warn")
      done()
    })
  })

})
