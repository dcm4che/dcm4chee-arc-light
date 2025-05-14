import {ComponentFixture, TestBed, waitForAsync} from '@angular/core/testing';

import { CreateWebappComponent } from './create-webapp.component';
import {MatDialogRef} from "@angular/material/dialog";


class CreateWebappDependenc{
}

describe('CreateWebappComponent', () => {
  let component: CreateWebappComponent;
  let fixture: ComponentFixture<CreateWebappComponent>;

    beforeEach(waitForAsync(() => {
         TestBed.configureTestingModule({
            declarations: [CreateWebappComponent],
            providers: [
                {provide: MatDialogRef, useClass: CreateWebappDependenc}
            ],
            teardown: {destroyAfterEach: false}
        })
        .compileComponents();
    }));

  beforeEach(() => {
    fixture = TestBed.createComponent(CreateWebappComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
