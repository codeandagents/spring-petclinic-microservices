'use strict';

angular.module('petForm')
    .controller('PetFormController', ['$http', '$state', '$stateParams', function ($http, $state, $stateParams) {
        var self = this;
        var ownerId = $stateParams.ownerId || 0;

        $http.get('api/customer/petTypes').then(function (resp) {
            self.types = resp.data;
        }).then(function () {

            var petId = $stateParams.petId || 0;

            if (petId) { // edit
                $http.get("api/customer/owners/" + ownerId + "/pets/" + petId).then(function (resp) {
                    self.pet = resp.data;
                    self.pet.birthDate = new Date(self.pet.birthDate);
                    self.petTypeId = "" + self.pet.type.id;
                });
            } else {
                $http.get('api/customer/owners/' + ownerId).then(function (resp) {
                    self.pet = {
                        owner: resp.data.firstName + " " + resp.data.lastName
                    };
                    self.petTypeId = "1";
                })

            }
        });

        self.errors = null;
        var now = new Date();
        self.today = now.getFullYear() + '-' + ('0' + (now.getMonth() + 1)).slice(-2) + '-' + ('0' + now.getDate()).slice(-2);

        self.submit = function () {
            self.errors = null;
            var id = self.pet.id || 0;

            var data = {
                id: id,
                name: self.pet.name,
                birthDate: self.pet.birthDate,
                typeId: self.petTypeId
            };

            var req;
            if (id) {
                req = $http.put("api/customer/owners/" + ownerId + "/pets/" + id, data, {handleErrorsLocally: true});
            } else {
                req = $http.post("api/customer/owners/" + ownerId + "/pets", data, {handleErrorsLocally: true});
            }

            req.then(function () {
                $state.go('ownerDetails', {ownerId: ownerId});
            }).catch(function (response) {
                var body = response && response.data;
                var list = response && response.status === 400 && body && angular.isArray(body.errors) ? body.errors : [];
                if (list.length) {
                    self.errors = list.map(function (e) {
                        return e.defaultMessage;
                    });
                } else {
                    self.errors = ["Could not save the pet"];
                }
            });
        };
    }]);
