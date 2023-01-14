import { Injectable } from "@angular/core"
import { EntityState, EntityStore, QueryEntity, StoreConfig } from "@datorama/akita"
import { HttpBasedService } from "./util/httpbased.service"
import { UiStore } from "./ui.store"
import { GlobalConfig } from "./global.config"
import { HttpClient } from "@angular/common/http"
import { SessionQuery } from "./session/state/session.query"
import { empty, Observable } from "rxjs"

export interface Person extends EntityState<Person, string> {
  id: string
  name: string
  place: string
}
@StoreConfig({name: "persons", idKey: "id"})
class PersonsStore extends EntityStore<Person> {
  constructor() {
    super()
  }
}

@Injectable({providedIn: "root"})
export class PersonsState extends HttpBasedService {
  
  private store = new PersonsStore()
  query = new QueryEntity<Person>(this.store)
  constructor(
    config: GlobalConfig,
    http: HttpClient,
    sessionQuery: SessionQuery,
    uiStore: UiStore
  ) {
    super(config.baseApiUrl, http, sessionQuery, uiStore)
  }

  selectAll(namePart: string): Observable<Person[]> {
    return this.query.selectAll({
      filterBy: p => p.name.startsWith(namePart),
      sortBy: "name"
    })
  }

  get() {
    this.request(this.GET, "persons", {200: l => this.store.set(l)})
  }
}
  


