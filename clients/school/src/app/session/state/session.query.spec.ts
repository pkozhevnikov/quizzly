import { SessionQuery } from './session.query'
import { SessionStore } from './session.store'

import { zip } from "rxjs"

describe('SessionQuery', () => {
  let query: SessionQuery
  let store: SessionStore

  beforeEach(() => {
    store = new SessionStore()
    query = new SessionQuery(store)
  })

  it('should create an instance', () => {
    expect(query).toBeTruthy()
  })

  it("should update loggedIn when login", done => {
    store.update({id: "off1", name: "off1 name"})
    zip(query.loggedIn$, query.userId$, query.userName$).subscribe(all => {
      expect(all).toEqual([true, "off1", "off1 name"])
      done()
    })
  })

  it("should update loggedIn when logout", done => {
    store.update({id: null, name: null})
    zip(query.loggedIn$, query.userId$, query.userName$).subscribe(all => {
      expect(all).toEqual([false, null, null])
      done()
    })
  })

})
