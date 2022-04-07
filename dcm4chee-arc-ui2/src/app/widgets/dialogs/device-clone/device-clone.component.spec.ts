import { ComponentFixture, TestBed } from '@angular/core/testing';

import { DeviceCloneComponent } from './device-clone.component';

describe('DeviceCloneComponent', () => {
  let component: DeviceCloneComponent;
  let fixture: ComponentFixture<DeviceCloneComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [ DeviceCloneComponent ]
    })
    .compileComponents();
  });

  beforeEach(() => {
    fixture = TestBed.createComponent(DeviceCloneComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

/*  it('should create', () => {
    expect(component).toBeTruthy();
  });*/
});
