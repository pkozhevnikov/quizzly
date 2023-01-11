import { Injectable } from '@angular/core';
import { QueryEntity } from '@datorama/akita';
import { QuizzesStore, QuizzesState } from './quizzes.store';

@Injectable({ providedIn: 'root' })
export class QuizzesQuery extends QueryEntity<QuizzesState> {

  all$ = this.selectAll()

  constructor(protected override store: QuizzesStore) {
    super(store);
  }

}
