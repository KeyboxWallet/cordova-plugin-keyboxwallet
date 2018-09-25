var exec = require('cordova/exec');

function requestPermission(opts, successCallback, errorCallback) {
    if (typeof opts === 'function') {
        errorCallback = successCallback;
        successCallback = opts;
        opts = {};
    }

    exec(successCallback, errorCallback, 'Gts', 'requestPermission', [{ 'opts': opts }]);
}

function getPublicKeyFromPath(path, successCallback, errorCallback) {
    exec(successCallback, errorCallback, 'Gts', 'getPublicKeyFromPath', [path]);
}

function signReq(jparam, successCallback, errorCallback) {
    exec(successCallback, errorCallback, 'Gts', 'signReq', [jparam]);
}

function multiplyReq(jparam, successCallback, errorCallback) {
    exec(successCallback, errorCallback, 'Gts', 'multiplyReq', [jparam]);
}

exports.requestPermission = requestPermission;
exports.getPublicKeyFromPath = getPublicKeyFromPath;
exports.signReq = signReq;
exports.multiplyReq = multiplyReq;
