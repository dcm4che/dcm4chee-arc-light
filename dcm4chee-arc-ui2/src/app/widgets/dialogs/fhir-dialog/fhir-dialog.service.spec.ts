import { TestBed } from '@angular/core/testing';

import { FhirDialogService } from './fhir-dialog.service';
import {DeviceConfiguratorService} from "../../../configuration/device-configurator/device-configurator.service";
import {WebAppsListService} from "../../../configuration/web-apps-list/web-apps-list.service";
import {J4careHttpService} from "../../../helpers/j4care-http.service";
import {AppService} from "../../../app.service";
import {DevicesService} from "../../../configuration/devices/devices.service";
import {AeListService} from "../../../configuration/ae-list/ae-list.service";
import {Hl7ApplicationsService} from "../../../configuration/hl7-applications/hl7-applications.service";
import {ControlService} from "../../../configuration/control/control.service";
class DummyComponent{
}
describe('FhirDialogService', () => {
  let service: FhirDialogService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        DeviceConfiguratorService,
        WebAppsListService,
        { provide: J4careHttpService, useClass: DummyComponent }
      ],
    });
    service = TestBed.inject(FhirDialogService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });
});
