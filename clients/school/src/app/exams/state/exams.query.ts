import { Injectable } from '@angular/core'
import { QueryEntity } from '@datorama/akita'
import { ExamsStore, ExamsState } from './exams.store'

@Injectable({ providedIn: 'root' })
export class ExamsQuery extends QueryEntity<ExamsState> {

  constructor(protected override store: ExamsStore) {
    super(store)
  }

}
