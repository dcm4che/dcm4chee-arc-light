import { ComponentFixture, TestBed } from '@angular/core/testing';

import { CreateWebappComponent } from './create-webapp.component';
import {MatLegacyDialogRef as MatDialogRef} from "@angular/material/legacy-dialog";


class CreateWebappDependenc{
}

describe('CreateWebappComponent', () => {
  let component: CreateWebappComponent;
  let fixture: ComponentFixture<CreateWebappComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
    declarations: [CreateWebappComponent],
    providers: [
        { provide: MatDialogRef, useClass: CreateWebappDependenc },
    ],
    teardown: { destroyAfterEach: false }
})
    .compileComponents();
  });

  beforeEach(() => {
    fixture = TestBed.createComponent(CreateWebappComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });
});
