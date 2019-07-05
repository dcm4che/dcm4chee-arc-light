import { Component } from '@angular/core';

@Component({
    selector: 'monitoring-tabs',
    template: `
    <ul class="nav nav-tabs" role="tablist">
        <li [permission]="{id:'tab-monitoring->associations',param:'visible'}" role="presentation" routerLinkActive="active"><a role="tab" routerLink="/monitoring/associations" routerLinkActive="active">Associations</a></li>
        <li [permission]="{id:'tab-monitoring->queues',param:'visible'}" role="presentation" routerLinkActive="active"><a role="tab" routerLink="/monitoring/queues" routerLinkActive="active">Queues</a></li>
        <li [permission]="{id:'tab-monitoring->export',param:'visible'}" role="presentation" routerLinkActive="active"><a role="tab" routerLink="/monitoring/export" routerLinkActive="active">Export</a></li>
        <li [permission]="{id:'tab-monitoring->external_retrieve',param:'visible'}" role="presentation" routerLinkActive="active"><a role="tab" routerLink="/monitoring/external" routerLinkActive="active">Retrieve</a></li>
        <li [permission]="{id:'tab-monitoring->diff',param:'visible'}" role="presentation" routerLinkActive="active"><a role="tab" routerLink="/monitoring/diff" routerLinkActive="active">Diff</a></li>
        
        <!--<li role="presentation" routerLinkActive="active"><a role="tab" routerLink="/monitoring/statistics" routerLinkActive="active">Statistics</a></li>-->
        <!--// <li role="presentation" routerLinkActive="active"><a role="tab" routerLink="/monitoring/lifecycle-management" routerLinkActive="active">Lifecycle managements</a></li>-->
        <li [permission]="{id:'tab-monitoring->storage_commitments',param:'visible'}" role="presentation" routerLinkActive="active"><a role="tab" routerLink="/monitoring/storage-commitment" routerLinkActive="active">Storage commitments</a></li>
        <li [permission]="{id:'tab-monitoring->storage_systems',param:'visible'}" role="presentation" routerLinkActive="active"><a role="tab" routerLink="/monitoring/storage-systems" routerLinkActive="active">Storage systems</a></li>
        <li [permission]="{id:'tab-monitoring->storage_verification',param:'visible'}" role="presentation" routerLinkActive="active"><a role="tab" routerLink="/monitoring/storage-verification" routerLinkActive="active">Storage verification</a></li>
        <li [permission]="{id:'tab-monitoring->metrics',param:'visible'}" role="presentation" routerLinkActive="active"><a role="tab" routerLink="/monitoring/metrics" routerLinkActive="active">Metrics</a></li>
    </ul>
  `
})
export class MonitoringTabsComponent {
}