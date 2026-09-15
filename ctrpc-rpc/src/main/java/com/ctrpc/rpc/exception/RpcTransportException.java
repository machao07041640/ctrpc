package com.ctrpc.rpc.exception;

import io.grpc.Status;

/** Transport-level failure, distinct from an application/business RPC error. */
public class RpcTransportException extends RpcException {
    private final Status.Code statusCode;
    public RpcTransportException(Status.Code statusCode, String message, Throwable cause) {
        super(503, message, cause);
        this.statusCode = statusCode;
    }
    public Status.Code getStatusCode() { return statusCode; }
}
