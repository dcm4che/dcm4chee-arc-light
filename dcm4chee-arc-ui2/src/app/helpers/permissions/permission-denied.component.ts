import { Component, OnInit } from '@angular/core';

@Component({
  selector: 'permission-denied',
  template: `
      <div class="main_content">
        <h2>
          Permission denied!
        </h2>
      </div>
  `,
  styles: [`
      h2{
          text-align: center;
          color: rgba(255, 0, 0, 0.87);
      }
  `]
})
export class PermissionDeniedComponent{
}
