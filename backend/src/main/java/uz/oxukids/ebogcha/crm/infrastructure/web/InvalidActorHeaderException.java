package uz.oxukids.ebogcha.crm.infrastructure.web;

final class InvalidActorHeaderException extends IllegalArgumentException {

    InvalidActorHeaderException() {
        super("X-Actor-User-Id must contain a valid UUID");
    }
}
