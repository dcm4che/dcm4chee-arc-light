import { Component, OnInit } from '@angular/core';

@Component({
    selector: 'monitoring-tabs',
    template: `
    <ul class="nav nav-tabs" role="tablist">
        <li role="presentation" routerLinkActive="active"><a role="tab" routerLink="/monitoring/queues" routerLinkActive="active">Queues</a></li>
        <li role="presentation" routerLinkActive="active"><a role="tab" routerLink="/monitoring/export" routerLinkActive="active">Export</a></li>
        <li role="presentation" routerLinkActive="active"><a role="tab" routerLink="/monitoring/external" routerLinkActive="active">External Retrieve</a></li>
        <li role="presentation" routerLinkActive="active"><a role="tab" routerLink="/monitoring/control" routerLinkActive="active">Control</a></li>
        <li role="presentation" routerLinkActive="active"><a role="tab" routerLink="/monitoring/associations" routerLinkActive="active">Associations</a></li>

        <li role="presentation" routerLinkActive="active"><a role="tab" routerLink="/monitoring/storage-commitment" routerLinkActive="active">Storage commitments</a></li>
        <li role="presentation" routerLinkActive="active"><a role="tab" routerLink="/monitoring/storage-systems" routerLinkActive="active">Storage systems</a></li>
    </ul>
  `
})
export class MonitoringTabsComponent {
}