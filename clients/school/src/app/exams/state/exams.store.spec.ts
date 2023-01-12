import { ExamsStore } from './exams.store';

describe('ExamsStore', () => {
  let store: ExamsStore;

  beforeEach(() => {
    store = new ExamsStore();
  });

  it('should create an instance', () => {
    expect(store).toBeTruthy();
  });

});
