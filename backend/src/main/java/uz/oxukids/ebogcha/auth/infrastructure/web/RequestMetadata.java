package uz.oxukids.ebogcha.auth.infrastructure.web;

public record RequestMetadata(
    String remoteAddress,
    String userAgent,
    String correlationId
) {
    public static RequestMetadata empty() {
        return new RequestMetadata(null, null, null);
    }
}
