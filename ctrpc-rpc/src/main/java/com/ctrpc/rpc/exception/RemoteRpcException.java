package com.ctrpc.rpc.exception;

public class RemoteRpcException extends RpcException {

    private static final long serialVersionUID = 1L;

    private final String remoteExceptionClass;
    private final String remoteStackTrace;
    private final boolean declared;

    public RemoteRpcException(
            int code,
            String remoteExceptionClass,
            String message,
            String remoteStackTrace,
            boolean declared) {
        super(code, message);
        this.remoteExceptionClass = remoteExceptionClass;
        this.remoteStackTrace = remoteStackTrace;
        this.declared = declared;
    }

    public String getRemoteExceptionClass() {
        return remoteExceptionClass;
    }

    public String getRemoteStackTrace() {
        return remoteStackTrace;
    }

    public boolean isDeclared() {
        return declared;
    }
}
