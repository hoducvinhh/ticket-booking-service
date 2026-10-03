package space.ducvinh.ticket_booking_service.common.exception;

import org.springframework.http.HttpStatus;

public class BusinessRuleViolationException extends AppException {
    public BusinessRuleViolationException(String message) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, "BUSINESS_RULE_VIOLATION", message);
    }

    public BusinessRuleViolationException(String errorCode, String message) {
        super(HttpStatus.UNPROCESSABLE_ENTITY, errorCode, message);
    }
}
