"use strict";

myApp.controller('StudyListCtrl', function ($scope, $window, $http, QidoService) {
    $scope.studies = [];
    $scope.limit = 20;
    $scope.aes = [];
    $scope.aet = null;
<<<<<<< HEAD
=======
    $scope.exporters;
    $scope.exporterID = null;
    $scope.rjnotes;
    $scope.rjnote = null;
>>>>>>> master
    $scope.filter = { orderby: "-StudyDate,-StudyTime" };
    $scope.studyDate = { from: '', to: ''};
    $scope.studyTime = { from: '', to: ''};
    $scope.queryStudies = function(offset) {
        QidoService.queryStudies(
            qidoURL(),
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
            qidoURL(),
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
            qidoURL(),
            series.attrs['0020000D'].Value[0],
            series.attrs['0020000E'].Value[0],
            createQueryParams(offset, { orderby: 'InstanceNumber'})
        )
        .then(function (res) {
            series.instances = res.data.map(function(attrs, index) {
                var numberOfFrames = valueOf(attrs['00280008']),
                    gspsQueryParams = createGSPSQueryParams(attrs),
                    video = isVideo(attrs);
                return {
                    offset: offset + index,
                    attrs: attrs,
                    showAttributes: false,
                    wadoQueryParams: {
                        studyUID: attrs['0020000D'].Value[0],
                        seriesUID: attrs['0020000E'].Value[0],
                        objectUID: attrs['00080018'].Value[0]
                    },
                    video: video,
                    numberOfFrames: numberOfFrames,
                    gspsQueryParams: gspsQueryParams,
                    views: createArray(video || numberOfFrames || gspsQueryParams.length || 1),
                    view: 1
                };
            });
            if (series.moreInstances = (series.instances.length > $scope.limit)) {
                series.instances.pop();
            }
        });
    };
    $scope.exportStudy = function(study) {
        $http.get(studyURL(study.attrs) + '/export/' + $scope.exporterID);
    };
    $scope.exportSeries = function(series) {
        $http.get(seriesURL(series.attrs) + '/export/' + $scope.exporterID);
    };
    $scope.exportInstance = function(instance) {
        $http.get(instanceURL(instance.attrs) + '/export/' + $scope.exporterID);
    };
    $scope.rejectStudy = function(study) {
        $http.get(studyURL(study.attrs) + '/reject/' + $scope.reject).then(function (res) {
            $scope.queryStudies($scope.studies[0].offset);
        });
    };
    $scope.rejectSeries = function(series) {
        $http.get(seriesURL(series.attrs) + '/reject/' + $scope.reject).then(function (res) {
            $scope.querySeries(series.study, series.study.series[0].offset);
        });
    };
    $scope.rejectInstance = function(instance) {
        $http.get(instanceURL(instance.attrs) + '/reject/' + $scope.reject).then(function (res) {
            $scope.queryInstances(instance.series, instance.series.instances[0].offset);
        });
    };
    $scope.deleteRejectedInstances = function() {
        $http.delete('../reject/' + $scope.reject).then(function (res) {
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
    function qidoURL() {
        return "../aets/" + $scope.aet + "/rs";
    }
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
        if (inst.video)
            return wadoURL(inst.wadoQueryParams, { contentType: 'video/mpeg' });
        if (inst.numberOfFrames)
            return wadoURL(inst.wadoQueryParams, { contentType: 'image/jpeg', frameNumber: inst.view });
        if (inst.gspsQueryParams.length)
            return wadoURL(inst.gspsQueryParams[inst.view - 1]);
        return wadoURL(inst.wadoQueryParams);
    }
    function wadoURL() {
        var i, url = "../aets/" + $scope.aet + "/wado?requestType=WADO";
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
    function isVideo(attrs) {
        var sopClass = valueOf(attrs['00080016']);
        return [
            '1.2.840.10008.5.1.4.1.1.77.1.1.1',
            '1.2.840.10008.5.1.4.1.1.77.1.2.1',
            '1.2.840.10008.5.1.4.1.1.77.1.4.1']
            .indexOf(sopClass) != -1 ? 1 : 0;
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
<<<<<<< HEAD
    function init() {
        $http.get("../aets").then(function (res) {
            $scope.aes = res.data;
            $scope.aet = res.data[0].title;
        })
    }
    init();
=======
    function initAETs(retries) {
        $http.get("../aets").then(
            function (res) {
                $scope.aes = res.data;
                $scope.aet = res.data[0].title;
            },
            function (res) {
                if (retries)
                    initAETs(retries-1);
            });
    }
    function initExporters(retries) {
        $http.get("../export").then(
            function (res) {
                $scope.exporters = res.data;
                $scope.exporterID = res.data[0].id;
            },
            function (res) {
                if (retries)
                    initExporters(retries-1);
            });
    }
    function initRjNotes(retries) {
        $http.get("../reject").then(
            function (res) {
                var rjnotes = res.data;
                rjnotes.sort(function (a, b) {
                    if (a.codeValue === "113039" && a.codingSchemeDesignator === "DCM")
                        return -1;
                    if (b.codeValue === "113039" && b.codingSchemeDesignator === "DCM")
                        return 1;
                    return 0;
                });
                $scope.rjnotes = rjnotes;
                $scope.reject = rjnotes[0].codeValue + "^" + rjnotes[0].codingSchemeDesignator;
            },
            function (res) {
                if (retries)
                    initRjNotes(retries-1);
            });
    }
    initAETs(1);
    initExporters(1);
    initRjNotes(1);
>>>>>>> master
});
