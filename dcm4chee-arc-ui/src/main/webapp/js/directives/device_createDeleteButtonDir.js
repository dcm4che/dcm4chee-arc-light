myApp.directive('createDeleteButton', function($compile) {
    return {
      restrict: 'E',
      scope: {
        part: '@part',
        createText: '@createText',
        deleteText: '@deleteText'
      },
      templateUrl: 'templates/device_createDeleteButton.html'
    };
  });