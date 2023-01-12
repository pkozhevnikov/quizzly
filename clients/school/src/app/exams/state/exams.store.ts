import { Injectable } from '@angular/core';
import { EntityState, EntityStore, StoreConfig } from '@datorama/akita';
import { Exam } from './exam.model';

export interface ExamsState extends EntityState<Exam> {}

@Injectable({ providedIn: 'root' })
@StoreConfig({ name: 'exams' })
export class ExamsStore extends EntityStore<ExamsState> {

  constructor() {
    super();
  }

}
