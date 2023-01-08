import { UiStore } from './ui.store';

describe('UiStore', () => {
  let store: UiStore;

  beforeEach(() => {
    store = new UiStore();
  });

  it('should create an instance', () => {
    expect(store).toBeTruthy();
  });

});
