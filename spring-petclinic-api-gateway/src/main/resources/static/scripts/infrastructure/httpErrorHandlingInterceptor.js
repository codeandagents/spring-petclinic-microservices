'use strict';

/**
 * Global HTTP errors handler.
 * Alerts for every failed request, unless the request sets config.handleErrorsLocally
 * (the caller then shows the error itself). Always rejects, so success handlers do not run.
 */
angular.module('infrastructure')
    .factory('HttpErrorHandlingInterceptor', ['$q', function ($q) {
        return {
            responseError: function (response) {
                if (!(response.config && response.config.handleErrorsLocally)) {
                    var error = response.data;
                    var message = (error && error.error) || "Request failed, please try again later";
                    if (error && angular.isArray(error.errors) && error.errors.length > 0) {
                        message += "\r\n" + error.errors.map(function (e) {
                            return e.field + ": " + e.defaultMessage;
                        }).join("\r\n");
                    }
                    alert(message);
                }
                return $q.reject(response);
            }
        };
    }]);
