import {Directive, ElementRef} from '@angular/core';
import {Input, HostListener} from "@angular/core/src/metadata/directives";

@Directive({
  selector: '[tooltip]'
})
export class TooltipDirective {
    @Input() tooltip: string;
    placeholderSet = false;
    div;


    constructor(private el: ElementRef) {
        if(this.tooltip){
            this.createPlaceholder();
        }
    }
    @HostListener('mouseenter') onMouseEnter() {
        if(!this.placeholderSet){
            this.createPlaceholder();
        }
        this.showTooltip();
    }
    @HostListener('mouseleave') onMouseLeave() {
        this.hideTooltip();
    }
    @HostListener('onmouseup') onMouseUp() {
        window.prompt("Copy to clipboard: Ctrl+C, Enter6", this.tooltip);

    }
    createPlaceholder(){
        this.div =  document.createElement("div")
        this.div.className="tooltip_container";
        let div2 =  document.createElement("div")
        div2.className="dir-tooltip animated";
        let text = document.createTextNode(this.tooltip);
        this.div.addEventListener("mouseup",()=>{
            window.prompt("Copy to clipboard: Ctrl+C, Enter", this.tooltip);
        });
        div2.appendChild(text);
        this.div.appendChild(div2);
        // this.el.nativeElement.addEventListener("mouseup",()=>{
        //     window.prompt("Copy to clipboard: Ctrl+C, Enter4", this.tooltip);
        // });
        this.el.nativeElement.appendChild(this.div);
        this.div.addEventListener("dblclick",()=>{
            window.prompt("Copy to clipboard: Ctrl+C, Enter", this.tooltip);
        });
        this.placeholderSet = true;
    }
    showTooltip(){
        this.div.classList.add("openflag");
        this.div.classList.remove('closeflag');
        setTimeout(()=>{
            if(this.div.classList.contains("openflag")){
                this.div.classList.add("show");
                this.div.classList.remove("closeflag");
                this.div.classList.remove("openflag");
            }
        },800);
    };
    hideTooltip(){
        this.div.classList.add("closeflag");
        this.div.classList.remove('openflag');
        setTimeout(()=>{
            if(this.div.classList.contains("closeflag")){
                this.div.classList.remove("closeflag");
                this.div.classList.remove("show");
            }
        },500);
    }
}
