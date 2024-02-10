package it.zwets.sms.gateway.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import it.zwets.sms.gateway.SmsGatewayConfiguration.Constants;

@JsonInclude(Include.NON_EMPTY)
public record SmsStatusResponse(
        @JsonProperty(Constants.OUT_FIELD_CLIENT_ID) String clientId,
        @JsonProperty(Constants.OUT_FIELD_CORREL_ID) String correlId,
        @JsonProperty(Constants.OUT_FIELD_TIMESTAMP) String timeStamp,
        @JsonProperty(Constants.OUT_FIELD_SMS_STATUS) String smsStatus,
        @JsonProperty(Constants.OUT_FIELD_RECALL_ID) String recallId,
        @JsonProperty(Constants.OUT_FIELD_ERROR_TEXT) String errorText) {
    
    private static String b(String s) {
        return s == null ? "" : s;
    }
    
    public String asTsv() {
        return "%s\t%s\t%s\t%s\t%s\t%s\n".formatted(b(timeStamp), b(clientId), b(correlId), b(smsStatus), b(recallId), b(errorText));
    }
}
       