import { Dcm4cheeArcUi2Page } from './app.po';

describe('dcm4chee-arc-ui2 App', function() {
  let page: Dcm4cheeArcUi2Page;

  beforeEach(() => {
    page = new Dcm4cheeArcUi2Page();
  });

  it('should display message saying app works', () => {
    page.navigateTo();
    expect(page.getParagraphText()).toEqual('app works!');
  });
});
