import { HttpClientTestingModule } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { QuizzesService } from './quizzes.service';
import { QuizzesStore } from './quizzes.store';

xdescribe('QuizzesService', () => {
  let quizzesService: QuizzesService;
  let quizzesStore: QuizzesStore;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [QuizzesService, QuizzesStore],
      imports: [ HttpClientTestingModule ]
    });

    quizzesService = TestBed.inject(QuizzesService);
    quizzesStore = TestBed.inject(QuizzesStore);
  });

  it('should be created', () => {
    expect(quizzesService).toBeDefined();
  });

});
