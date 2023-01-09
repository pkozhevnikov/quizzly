import { Injectable } from '@angular/core'
import { Query } from '@datorama/akita'
import { UiStore, UiState } from './ui.store'

@Injectable({ providedIn: 'root' })
export class UiQuery extends Query<UiState> {

  notif$ = this.select("notif")

  constructor(protected override store: UiStore) {
    super(store)
  }

}
