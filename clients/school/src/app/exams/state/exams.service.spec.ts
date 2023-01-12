import { HttpClientTestingModule } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ExamsService } from './exams.service';
import { ExamsStore } from './exams.store';

describe('ExamsService', () => {
  let examsService: ExamsService;
  let examsStore: ExamsStore;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [ExamsService, ExamsStore],
      imports: [ HttpClientTestingModule ]
    });

    examsService = TestBed.inject(ExamsService);
    examsStore = TestBed.inject(ExamsStore);
  });

  it('should be created', () => {
    expect(examsService).toBeDefined();
  });

});
