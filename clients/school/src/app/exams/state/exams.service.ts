import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { ID } from '@datorama/akita';
import { tap } from 'rxjs/operators';
import { Exam } from './exam.model';
import { ExamsStore } from './exams.store';

@Injectable({ providedIn: 'root' })
export class ExamsService {

  constructor(private examsStore: ExamsStore, private http: HttpClient) {
  }


  get() {
    return this.http.get<Exam[]>('https://api.com').pipe(tap(entities => {
      this.examsStore.set(entities);
    }));
  }

  add(exam: Exam) {
    this.examsStore.add(exam);
  }

  update(id, exam: Partial<Exam>) {
    this.examsStore.update(id, exam);
  }

  remove(id: ID) {
    this.examsStore.remove(id);
  }

}
