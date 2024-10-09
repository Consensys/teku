package tech.pegasys.teku.ethereum.executionclient.web3j;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public final class JsonRpcErrorCodes {
    public static final int PARSE_ERROR = -32700;
    public static final int INVALID_REQUEST = -32600;
    public static final int METHOD_NOT_FOUND = -32601;
    public static final int INVALID_PARAMS = -32602;
    public static final int INTERNAL_ERROR = -32603;
    public static final int SERVER_ERROR_RANGE_START = -32000;
    public static final int SERVER_ERROR_RANGE_END = -32099;

    private static final Map<Integer, String> ERROR_MESSAGES;

    static {
        Map<Integer, String> messages = new HashMap<>();
        messages.put(PARSE_ERROR, "Parse error");
        messages.put(INVALID_REQUEST, "Invalid request");
        messages.put(METHOD_NOT_FOUND, "Method not found");
        messages.put(INVALID_PARAMS, "Invalid params");
        messages.put(INTERNAL_ERROR, "Internal error");
        messages.put(SERVER_ERROR_RANGE_START, "Server error");
        ERROR_MESSAGES = Collections.unmodifiableMap(messages);
    }

    private JsonRpcErrorCodes() {
        // Utility class, do not instantiate
    }

    public static String getErrorMessage(int errorCode) {
        if (isServerError(errorCode)) {
            return ERROR_MESSAGES.get(SERVER_ERROR_RANGE_START);
        }
        return ERROR_MESSAGES.getOrDefault(errorCode, "Unknown error");
    }

    public static boolean isServerError(int errorCode) {
        return errorCode >= SERVER_ERROR_RANGE_END && errorCode <= SERVER_ERROR_RANGE_START;
    }

    public static boolean isStandardError(int errorCode) {
        return ERROR_MESSAGES.containsKey(errorCode) || isServerError(errorCode);
    }
}