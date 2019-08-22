import { Component } from '@angular/core';

@Component({
  selector: 'config-tab',
  template: `
      <ul class="nav nav-tabs" role="tablist">
          <li [permission]="{id:'tab-configuration->devices',param:'visible'}"  role="presentation" routerLinkActive="active"><a aria-controls="device_modus" routerLink="/device/devicelist" role="tab" routerLinkActive="active">Devices</a></li>
          <li [permission]="{id:'tab-configuration->ae_list',param:'visible'}"  role="presentation" routerLinkActive="active"><a aria-controls="aet_modus" routerLink="/device/aelist" role="tab"  routerLinkActive="active">AE list</a></li>
          <li [permission]="{id:'tab-configuration->web_apps_list',param:'visible'}"  role="presentation" routerLinkActive="active"><a aria-controls="aet_modus" routerLink="/device/webappslist" role="tab"  routerLinkActive="active">Web Apps list</a></li>
          <li [permission]="{id:'tab-configuration->hl7_applications',param:'visible'}"  role="presentation" routerLinkActive="active"><a aria-controls="aet_modus" routerLink="/device/hl7applications" role="tab"  routerLinkActive="active">Hl7 Applications</a></li>
          <li [permission]="{id:'tab-monitoring->control',param:'visible'}" role="presentation" routerLinkActive="active"><a role="tab" routerLink="/monitoring/control" routerLinkActive="active">Control</a></li>
      </ul>
  `
})
export class ConfigTabComponent{}
