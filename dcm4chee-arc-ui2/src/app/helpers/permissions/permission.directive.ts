import {Directive, ElementRef, Input, OnInit} from '@angular/core';
import {PermissionService} from "./permission.service";

@Directive({
  selector: '[permission]'
})
export class PermissionDirective implements OnInit{

  @Input() permission;

  constructor(private el: ElementRef, private permisssionService:PermissionService) { }

  ngOnInit(){
      let check = this.permisssionService.checkVisibility(this.permission);
      if(typeof check === 'object' && check.source){
        check.subscribe((res)=>{
            if(!res)
                this.el.nativeElement.parentNode.removeChild(this.el.nativeElement);
        },(err)=>{
          console.log("Error on checking directives",err);
        });
      }else
        if(!check)
          this.el.nativeElement.parentNode.removeChild(this.el.nativeElement);
  }

}
