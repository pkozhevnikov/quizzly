import { Injectable } from '@angular/core';
import { EntityState, EntityStore, StoreConfig } from '@datorama/akita';
import { Quiz } from './quiz.model';

export interface QuizzesState extends EntityState<Quiz> {}

@Injectable({ providedIn: 'root' })
@StoreConfig({ name: 'quizzes' })
export class QuizzesStore extends EntityStore<QuizzesState> {

  constructor() {
    super();
  }

}
