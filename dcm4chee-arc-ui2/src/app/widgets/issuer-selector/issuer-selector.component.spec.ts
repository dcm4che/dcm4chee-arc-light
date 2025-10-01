import { ComponentFixture, TestBed, waitForAsync } from '@angular/core/testing';

import { IssuerSelectorComponent } from './issuer-selector.component';
import {AppService} from '../../app.service';
class MockAppService {
  global:any = {};
  someMethod() { return 'mocked value'; }
}
describe('IssuerSelectorComponent', () => {
  let component: IssuerSelectorComponent;
  let fixture: ComponentFixture<IssuerSelectorComponent>;

  beforeEach(waitForAsync(() => {
    TestBed.configureTestingModule({
    declarations: [],
      providers:[{ provide: AppService, useClass: MockAppService }],
    teardown: { destroyAfterEach: false }
})
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(IssuerSelectorComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
  it('Should extract the model parts from string',()=>{
    component.model = "test^^^selam";

  })
});
