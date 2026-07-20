import { SvgDirective } from './svg.directive';

describe('SvgDirective', () => {
  it('should create an instance', () => {
    const mockElementRef = {} as any;
    const mockHttp = {} as any;
    const directive = new SvgDirective(mockElementRef, mockHttp);
    expect(directive).toBeTruthy();
  });
});
