package space.ducvinh.ticket_booking_service.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {
    private boolean success;
    private int status;
    private String errorCode;
    private String message;
    private String path;
    private Map<String, String> fieldErrors;
    @Builder.Default
    private Instant timestamp = Instant.now();
}
