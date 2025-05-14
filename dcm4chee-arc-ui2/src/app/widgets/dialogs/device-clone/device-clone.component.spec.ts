import {ComponentFixture, TestBed, waitForAsync} from '@angular/core/testing';

import { DeviceCloneComponent } from './device-clone.component';

describe('DeviceCloneComponent', () => {
  let component: DeviceCloneComponent;
  let fixture: ComponentFixture<DeviceCloneComponent>;

  beforeEach(waitForAsync(() => {
    TestBed.configureTestingModule({
        declarations: [DeviceCloneComponent],
        teardown: { destroyAfterEach: false }
    })
    .compileComponents();
  }));

  beforeEach(() => {
    fixture = TestBed.createComponent(DeviceCloneComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

/*  it('should create', () => {
    expect(component).toBeTruthy();
  });*/
});
