import { ExamsQuery } from './exams.query';
import { ExamsStore } from './exams.store';

describe('ExamsQuery', () => {
  let query: ExamsQuery;

  beforeEach(() => {
    query = new ExamsQuery(new ExamsStore);
  });

  it('should create an instance', () => {
    expect(query).toBeTruthy();
  });

});
