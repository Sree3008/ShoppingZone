package com.eshoppingzone.product.audit.context;

public class AuditContext {

    private static final ThreadLocal<AuditContextData> CONTEXT = ThreadLocal.withInitial(AuditContextData::new);

    public static AuditContextData get() {
        return CONTEXT.get();
    }

    public static void clear() {
        CONTEXT.remove();
    }

    public static class AuditContextData {
        private String correlationId;
        private String ipAddress;
        private String userAgent;
        private Long actorUserId;
        private String actorUsername;
        private String actorRole;
        private String httpMethod;
        private String endpoint;

        public String getCorrelationId() {
            return correlationId;
        }

        public void setCorrelationId(String correlationId) {
            this.correlationId = correlationId;
        }

        public String getIpAddress() {
            return ipAddress;
        }

        public void setIpAddress(String ipAddress) {
            this.ipAddress = ipAddress;
        }

        public String getUserAgent() {
            return userAgent;
        }

        public void setUserAgent(String userAgent) {
            this.userAgent = userAgent;
        }

        public Long getActorUserId() {
            return actorUserId;
        }

        public void setActorUserId(Long actorUserId) {
            this.actorUserId = actorUserId;
        }

        public String getActorUsername() {
            return actorUsername;
        }

        public void setActorUsername(String actorUsername) {
            this.actorUsername = actorUsername;
        }

        public String getActorRole() {
            return actorRole;
        }

        public void setActorRole(String actorRole) {
            this.actorRole = actorRole;
        }

        public String getHttpMethod() {
            return httpMethod;
        }

        public void setHttpMethod(String httpMethod) {
            this.httpMethod = httpMethod;
        }

        public String getEndpoint() {
            return endpoint;
        }

        public void setEndpoint(String endpoint) {
            this.endpoint = endpoint;
        }
    }
}
