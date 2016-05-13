myApp.directive("tooltip", function ($window, $log, $compile) {
    var tooltipExecute = function(scope, element, text){
                angular.element(element).bind("mouseenter",function(e){
                    var $this = this;
                    var tooltip = angular.element(this).find(".tooltip_container");
                    tooltip.addClass("openflag");

                    setTimeout(function(){
                        if(tooltip.hasClass("openflag")){

                            tooltip
                                .css("overflow","visible")
                                .removeClass('fadeOut')
                                .removeClass('closeflag')
                                .show()
                                .removeClass('openflag')
                                .addClass('fadeIn');
                        }
                    }, 800);
                });
                angular.element(element).bind("mouseleave",function(){
                        var $this = this;
                        var tooltip = angular.element($this).find(".tooltip_container");
                        tooltip.addClass("closeflag");
                        tooltip.removeClass('openflag');
                        setTimeout(function(){
                            if(tooltip.hasClass('closeflag')){
                                tooltip
                                    .removeClass('closeflag')
                                    .removeClass('fadeIn')
                                    .hide()
                                    .addClass('fadeOut');
                            }
                        }, 500);
                });
                var x    = angular.element('<div class="tooltip_container"><div class="dir-tooltip animated">'+text+'</div></div>');
                element.append(x);
                x.hide();
                $compile(x)(scope);
    };

    return{
        restrict: 'A',
        link: function(scope, element, attrs) {
            if(attrs.tooltip != "" && attrs.tooltip != undefined){
                tooltipExecute(scope, element, attrs.tooltip);
                //Check again if the tooltip-text was changed (on the first time the rendering process was not finished)
                setTimeout(function(){
                    if(angular.element(element).text().length != angular.element(element).find(".tooltip_container .dir-tooltip").text().length && angular.element(element).find(".tooltip_container .dir-tooltip").text().length > 0){
                            angular.element(element).find(".tooltip_container").remove();
                            tooltipExecute(scope, element, angular.element(element).attr("tooltip"));
                    }   
                },500);
            }
        }
    }
});