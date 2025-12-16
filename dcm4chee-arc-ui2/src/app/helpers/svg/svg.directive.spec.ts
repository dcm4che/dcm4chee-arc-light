import { SvgDirective } from './svg.directive';

describe('SvgDirective', () => {
  it('should create an instance', () => {
    const mockElementRef = {} as any;
    const directive = new SvgDirective(mockElementRef);
    expect(directive).toBeTruthy();
  });
});
