import { TestBed, inject } from '@angular/core/testing';

import { WebAppsListService } from './web-apps-list.service';
import {AeListService} from "../ae-list/ae-list.service";
import {DeviceConfiguratorService} from "../device-configurator/device-configurator.service";
import {DevicesService} from "../devices/devices.service";
import {J4careHttpService} from "../../helpers/j4care-http.service";
import {Hl7ApplicationsService} from "../hl7-applications/hl7-applications.service";
import {AppService} from "../../app.service";
class MyServiceDependencyStub {
}
describe('WebAppsListService', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
          WebAppsListService,
          { provide: J4careHttpService, useClass: MyServiceDependencyStub },
          { provide: DevicesService, useClass: MyServiceDependencyStub },
          { provide: AeListService, useClass: MyServiceDependencyStub },
      ]
    });
  });

  it('should be created', inject([WebAppsListService], (service: WebAppsListService) => {
    expect(service).toBeTruthy();
  }));
});
