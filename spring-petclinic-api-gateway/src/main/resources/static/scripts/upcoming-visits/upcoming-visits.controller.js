'use strict';

angular.module('upcomingVisits')
    .controller('UpcomingVisitsController', ['$http', function ($http) {
        var self = this;

        $http.get('api/gateway/visits/upcoming').then(function (resp) {
            self.visits = resp.data.items;
        });
    }]);
