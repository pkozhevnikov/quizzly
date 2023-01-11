import { QuizzesStore } from './quizzes.store';

describe('QuizzesStore', () => {
  let store: QuizzesStore;

  beforeEach(() => {
    store = new QuizzesStore();
  });

  it('should create an instance', () => {
    expect(store).toBeTruthy();
  });

});
