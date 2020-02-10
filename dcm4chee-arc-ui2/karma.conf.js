// Karma configuration file, see link for more information
// https://karma-runner.github.io/0.13/config/configuration-file.html

module.exports = function (config) {
    config.set({
        basePath: '',
        frameworks: ['jasmine', '@angular-devkit/build-angular'],
        plugins: [
            require('karma-jasmine'),
            require('karma-chrome-launcher'),
            require('karma-jasmine-html-reporter'),
            require('karma-coverage-istanbul-reporter'),
            require('@angular-devkit/build-angular/plugins/karma')
        ],
        client:{
            clearContext: false // leave Jasmine Spec Runner output visible in browser
        },
        mime: {
            'text/x-typescript': ['ts','tsx']
        },
        coverageIstanbulReporter: {
            dir: require('path').join(__dirname, './coverage/dcm4chee-arc-ui2'),
            reports: [ 'html', 'lcovonly', 'text-summary' ],
            fixWebpackSourcePaths: true
        },
        files:[
            "src/app/constants/dcm4che-dict-names.js",
            "src/app/constants/dcm4chee-arc-dict-names.js",
            "src/app/constants/elscint-dict-names.js",
            "src/app/constants/dcm4che-dict-cuids.js",
            "src/app/constants/dcm4che-dict-tsuids.js",
            "src/app/constants/elscint-dict-names.js"
        ],
        angularCli: {
            environment: 'dev'
        },
        reporters: config.angularCli && config.angularCli.codeCoverage
            ? ['progress', 'coverage-istanbul']
            : ['progress', 'kjhtml'],
        port: 9876,
        colors: true,
        logLevel: config.LOG_INFO,
        autoWatch: true,
        browsers: ['Chrome'],
        singleRun: false
    });
};
