import {Directive, ElementRef, Input, OnInit} from '@angular/core';
import {PermissionService} from "./permission.service";

@Directive({
  selector: '[permission]'
})
export class PermissionDirective implements OnInit{

  @Input() permission;

  constructor(private el: ElementRef, private permisssionService:PermissionService) { }

  ngOnInit(){
      if(!this.permisssionService.checkMenuTabVisibility(this.permission))
        this.el.nativeElement.parentNode.removeChild(this.el.nativeElement);
  }

}
