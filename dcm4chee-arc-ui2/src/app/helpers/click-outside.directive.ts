import {Directive, ElementRef, Output, EventEmitter, HostListener, Input} from '@angular/core';

@Directive({
    selector: '[clickOutside]'
})
export class ClickOutsideDirective {
    constructor(private _elementRef : ElementRef) {
    }

    @Output()
    public clickOutside = new EventEmitter();
    @Input() clickOutsideExceptionClass;

    @HostListener('document:click', ['$event.target'])
    public onClick(targetElement) {
        let exception;
        const clickedInside = this._elementRef.nativeElement.contains(targetElement);
        if(this.clickOutsideExceptionClass && targetElement.classList && targetElement.classList.length > 0){
            if(Array.isArray(this.clickOutsideExceptionClass)){
                this.clickOutsideExceptionClass.forEach(css=>{
                    exception = exception || targetElement.classList.contains(css);
                })
            }else{
                exception = targetElement.classList.contains(this.clickOutsideExceptionClass);
            }
        }
        if (!clickedInside && !exception) {
            this.clickOutside.emit(null);
        }
    }
}