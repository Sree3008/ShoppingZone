package com.eshoppingzone.cart.audit.context;

public class AuditContext {

    private static final ThreadLocal<String> CORRELATION_ID = new ThreadLocal<>();
    private static final ThreadLocal<String> CLIENT_IP = new ThreadLocal<>();
    private static final ThreadLocal<String> USER_AGENT = new ThreadLocal<>();
    private static final ThreadLocal<String> HTTP_METHOD = new ThreadLocal<>();
    private static final ThreadLocal<String> REQUEST_URI = new ThreadLocal<>();

    public static void setCorrelationId(String correlationId) { CORRELATION_ID.set(correlationId); }
    public static String getCorrelationId() { return CORRELATION_ID.get(); }

    public static void setClientIp(String clientIp) { CLIENT_IP.set(clientIp); }
    public static String getClientIp() { return CLIENT_IP.get(); }

    public static void setUserAgent(String userAgent) { USER_AGENT.set(userAgent); }
    public static String getUserAgent() { return USER_AGENT.get(); }

    public static void setHttpMethod(String httpMethod) { HTTP_METHOD.set(httpMethod); }
    public static String getHttpMethod() { return HTTP_METHOD.get(); }

    public static void setRequestUri(String requestUri) { REQUEST_URI.set(requestUri); }
    public static String getRequestUri() { return REQUEST_URI.get(); }

    public static void clear() {
        CORRELATION_ID.remove();
        CLIENT_IP.remove();
        USER_AGENT.remove();
        HTTP_METHOD.remove();
        REQUEST_URI.remove();
    }
}
