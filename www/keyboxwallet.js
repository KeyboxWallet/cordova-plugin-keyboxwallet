var exec = require('cordova/exec');

function requestPermission(opts, successCallback, errorCallback) {
    if (typeof opts === 'function') {
        errorCallback = successCallback;
        successCallback = opts;
        opts = {};
    }

    exec(successCallback, errorCallback, 'keyboxwallet', 'requestPermission', [{ 'opts': opts }]);
}

function getPublicKeyFromPath(path, successCallback, errorCallback) {
    exec(successCallback, errorCallback, 'keyboxwallet', 'getPublicKeyFromPath', [path]);
}

function signReq(jparam, successCallback, errorCallback) {
    exec(successCallback, errorCallback, 'keyboxwallet', 'signReq', [jparam]);
}

function multiplyReq(jparam, successCallback, errorCallback) {
    exec(successCallback, errorCallback, 'keyboxwallet', 'multiplyReq', [jparam]);
}

exports.requestPermission = requestPermission;
exports.getPublicKeyFromPath = getPublicKeyFromPath;
exports.signReq = signReq;
exports.multiplyReq = multiplyReq;
