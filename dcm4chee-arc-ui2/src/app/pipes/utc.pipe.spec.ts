import { UtcPipe } from './utc.pipe';

describe('UtcPipe', () => {
  it('create an instance', () => {
    const pipe = new UtcPipe();
    expect(pipe).toBeTruthy();
  });
});
