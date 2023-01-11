import { QuizzesQuery } from './quizzes.query';
import { QuizzesStore } from './quizzes.store';

describe('QuizzesQuery', () => {
  let query: QuizzesQuery;

  beforeEach(() => {
    query = new QuizzesQuery(new QuizzesStore);
  });

  it('should create an instance', () => {
    expect(query).toBeTruthy();
  });

});
