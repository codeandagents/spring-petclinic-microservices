'use strict';

angular.module('upcomingVisits', ['ui.router'])
    .config(['$stateProvider', function ($stateProvider) {
        $stateProvider
            .state('upcomingVisits', {
                parent: 'app',
                url: '/upcoming-visits',
                template: '<upcoming-visits></upcoming-visits>'
            })
    }]);
