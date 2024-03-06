import { TestBed } from '@angular/core/testing';

import { KeycloakHelperService } from './keycloak-helper.service';
import {MatDialogRef} from "@angular/material/dialog";
import {KeycloakService} from "./keycloak.service";
import {J4careHttpService} from "../j4care-http.service";
class KeycloakHelperServiceDependency{
}
describe('KeycloakHelperService', () => {
  let service: KeycloakHelperService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        { provide: KeycloakService, useClass: KeycloakHelperServiceDependency },
        { provide: J4careHttpService, useClass: KeycloakHelperServiceDependency }
      ],
    });
    service = TestBed.inject(KeycloakHelperService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });
});
