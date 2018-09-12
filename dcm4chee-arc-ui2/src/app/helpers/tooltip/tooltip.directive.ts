import {Directive, ElementRef} from '@angular/core';
import {Input, HostListener} from '@angular/core';
import * as _ from 'lodash';
import {AppService} from "../../app.service";

@Directive({
    selector: '[tooltip]'
})
export class TooltipDirective {
    @Input() tooltip: string;
    placeholderSet = false;
    div;
    i;
    spanText;

    constructor(
        private el: ElementRef,
        private mainservice:AppService
        ) {
        if (this.tooltip){
            this.createPlaceholder();
        }
    }
    @HostListener('mouseenter') onMouseEnter() {
        if (!this.placeholderSet){
            this.createPlaceholder();
            this.showTooltip();
        }else{
            this.showTooltip();
        }
    }
    @HostListener('mouseleave') onMouseLeave() {
        if (this.placeholderSet){
            this.hideTooltip();
        }
    }
    @HostListener('onmouseup') onMouseUp() {
        window.prompt('Copy to clipboard: Ctrl+C, Enter', this.tooltip);
    }
    createPlaceholder(){
        if(this.tooltip && this.tooltip.length > 1){
            this.div =  document.createElement('div');
            this.i = document.createElement('i');
            this.i.className = "glyphicon glyphicon-duplicate";
            this.spanText = document.createElement('span');
            // this.i.setAttribute("title","Copy text to clipboard");
            this.i.title = "Copy text to clipboard";
            this.div.className = 'tooltip_container';
            let div2 =  document.createElement('div');
            div2.className = 'dir-tooltip animated';
            let br = document.createElement('br');

            this.i.addEventListener('mouseup', () => {
                copyToClipboard();
            });
            this.i.addEventListener('dblclick', () => {
                copyToClipboard();
            });

            let text;
            if (_.includes(this.tooltip, '<br>')){
                let textArray = this.tooltip.split('<br>');
                _.forEach(textArray, (m, i) => {
                    div2.appendChild(br);
                    this.spanText.appendChild(document.createTextNode(m));
                    div2.appendChild(this.spanText);
                    // div2.appendChild(document.createTextNode(m));
                });
            }else{
                text = document.createTextNode(this.tooltip);
                this.spanText.appendChild(text);
                div2.appendChild(this.spanText);
            }
            div2.appendChild(this.i);
            this.div.appendChild(div2);

            // this.el.nativeElement.addEventListener("mouseup",()=>{
            //     window.prompt("Copy to clipboard: Ctrl+C, Enter4", this.tooltip);
            // });
            this.el.nativeElement.appendChild(this.div);
            this.placeholderSet = true;
        }
        let copyToClipboard = ()=>{
            const el = document.createElement('textarea');
            el.value = this.tooltip;
            document.body.appendChild(el);
            el.select();
            document.execCommand('copy');
            document.body.removeChild(el);
            this.mainservice.setMessage({
                'title': 'Info',
                'text': 'Text copied successfully in the clipboard',
                'status': 'info'
            });
        }
    }
    showTooltip(){
        if(this.placeholderSet){
            this.div.classList.add('openflag');
            this.div.classList.remove('closeflag');
            setTimeout(() => {
                if (this.div.classList.contains('openflag')){
                    this.div.classList.add('show');
                    this.div.classList.remove('closeflag');
                    this.div.classList.remove('openflag');
                }
            }, 800);
        }
    };
    hideTooltip(){
        if(this.placeholderSet){
            this.div.classList.add('closeflag');
            this.div.classList.remove('openflag');
            setTimeout(() => {
                if (this.div.classList.contains('closeflag')){
                    this.div.classList.remove('closeflag');
                    this.div.classList.remove('show');
                }
            }, 1000);
        }
    }
}
