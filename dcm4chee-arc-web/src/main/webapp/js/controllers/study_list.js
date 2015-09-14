"use strict";

myApp.controller('StudyListCtrl', function ($scope, $window, QidoService) {
    $scope.studies = [];
    $scope.limit = 20;
    $scope.qidoURL = "http://localhost:8080/dcm4chee-arc/aets/DCM4CHEE/rs";
    $scope.wadoURL = "http://localhost:8080/dcm4chee-arc/aets/DCM4CHEE/wado";
    $scope.filter = { orderby: "-StudyDate,-StudyTime" };
    $scope.studyDate = { from: '', to: ''};
    $scope.studyTime = { from: '', to: ''};
    $scope.queryStudies = function(offset) {
        QidoService.queryStudies(
            $scope.qidoURL,
            createQueryParams(offset, createStudyFilterParams())
        ).then(function (res) {
            $scope.studies = res.data.map(function(attrs, index) {
                return {
                    offset: offset + index,
                    attrs: attrs,
                    series: null,
                    showAttributes: false
                };
            });
        });
    };
    $scope.querySeries = function(study, offset) {
        QidoService.querySeries(
            $scope.qidoURL,
            study.attrs['0020000D'].Value[0],
            createQueryParams(offset, { orderby: 'SeriesNumber'})
        ).then(function (res) {
            study.series = res.data.map(function(attrs, index) {
                return {
                    offset: offset + index,
                    attrs: attrs,
                    instances: null,
                    showAttributes: false
                 };
            });
        });
    };
    $scope.queryInstances = function (series, offset) {
        QidoService.queryInstances(
            $scope.qidoURL,
            series.attrs['0020000D'].Value[0],
            series.attrs['0020000E'].Value[0],
            createQueryParams(offset, { orderby: 'InstanceNumber'})
        )
        .then(function (res) {
            series.instances = res.data.map(function(attrs, index) {
                var numberOfFrames = valueOf(attrs['00280008']),
                    gspsQueryParams = createGSPSQueryParams(attrs);
                return {
                    offset: offset + index,
                    attrs: attrs,
                    showAttributes: false,
                    wadoQueryParams: {
                        studyUID: attrs['0020000D'].Value[0],
                        seriesUID: attrs['0020000E'].Value[0],
                        objectUID: attrs['00080018'].Value[0]
                    },
                    numberOfFrames: numberOfFrames,
                    gspsQueryParams: gspsQueryParams,
                    views: createArray(numberOfFrames || gspsQueryParams.length || 1),
                    view: 1
                };
            });
        });
    };
    $scope.downloadURL = function (inst, transferSyntax) {
        var exQueryParams = { contentType: 'application/dicom' };
        if (transferSyntax)
            exQueryParams.transferSyntax = transferSyntax;
        return wadoURL(inst.wadoQueryParams, exQueryParams);
    };
    $scope.viewInstance = function (inst) {
        $window.open(renderURL(inst));
    };
    $scope.studyRowspan = function(study) {
        var span = study.showAttributes ? 2 : 1;
        return study.series
            ? study.series.reduce(
                function(sum, series) {
                    return sum + $scope.seriesRowspan(series);
                },
                span+1)
            : span;
    };
    $scope.seriesRowspan = function(series) {
        var span = series.showAttributes ? 2 : 1;
        return series.instances
            ? series.instances.reduce(
                function (sum, instance) {
                    return sum + $scope.instanceRowspan(instance);
                },
                span + 1)
            : span;
    };
    $scope.instanceRowspan = function(instance) {
        return instance.showAttributes ? 2 : 1;
    };
    $scope.hasNext = function(objs) {
        return objs && (objs.length === $scope.limit);
    };
    $scope.hasPrev = function(objs) {
        return objs && objs.length && objs[0].offset;
    };
    $scope.prevOffset = function(objs) {
        return Math.max(0, objs[0].offset - $scope.limit);
    };
    $scope.nextOffset = function(objs) {
        return objs[0].offset + $scope.limit;
    };
    function createStudyFilterParams() {
        var filter = angular.extend({}, $scope.filter);
        appendFilter(filter, "StudyDate", $scope.studyDate, /-/g);
        appendFilter(filter, "StudyTime", $scope.studyTime, /:/g);
        return filter;
    }
    function appendFilter(filter, key, range, regex) {
        var value = range.from.replace(regex, '');
        if (range.to !== range.from)
            value += '-' + range.to.replace(regex, '');
        if (value.length)
            filter[key] = value;
    }
    function createQueryParams(offset, filter) {
        var params = {
            includefield: 'all',
            offset: offset,
            limit: $scope.limit
        };
        angular.forEach(filter, function(value, key) {
            if (value)
                params[key] = value;
        }, params);
        return params;
    }
    function renderURL(inst) {
        if (inst.numberOfFrames)
            return wadoURL(inst.wadoQueryParams, { contentType: 'image/jpeg', frameNumber: inst.view });
        if (inst.gspsQueryParams.length)
            return wadoURL(inst.gspsQueryParams[inst.view - 1]);
        return wadoURL(inst.wadoQueryParams);
    }
    function wadoURL() {
        var i, url = $scope.wadoURL + "?requestType=WADO";
        for (i = 0; i < arguments.length; i++) {
            angular.forEach(arguments[i], function(value, key) {
                url += '&' + key + '=' + value;
            });
        };
        return url;
    }
    function createGSPSQueryParams(attrs) {
        var sopClass = valueOf(attrs['00080016']),
            refSeries = valuesOf(attrs['00081115']),
            queryParams = [];
        if (sopClass === '1.2.840.10008.5.1.4.1.1.11.1' && refSeries) {
            refSeries.forEach(function(seriesRef) {
                valuesOf(seriesRef['00081140']).forEach(function(objRef) {
                    queryParams.push({
                        studyUID: attrs['0020000D'].Value[0],
                        seriesUID: seriesRef['0020000E'].Value[0],
                        objectUID: objRef['00081155'].Value[0],
                        contentType: 'image/jpeg',
                        frameNumber: valueOf(objRef['00081160']) || 1,
                        presentationSeriesUID: attrs['0020000E'].Value[0],
                        presentationUID: attrs['00080018'].Value[0]
                     })
                })
            })
        }
        return queryParams;
    }
    function valuesOf(attr) {
        return attr && attr.Value;
    }
    function valueOf(attr) {
        return attr && attr.Value[0];
    }
    function createArray(n) {
        var a = [];
        for (var i = 1; i <= n; i++)
            a.push(i);
        return a;
    }

});
