package it.zwets.sms.gateway.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import it.zwets.sms.gateway.SmsGatewayConfiguration.Constants;

@JsonInclude(Include.NON_EMPTY)
public record InboundSms(
        @JsonProperty(Constants.OUT_FIELD_SENDER) String sender,
        @JsonProperty(Constants.OUT_FIELD_RECIPIENT) String recipient,
        @JsonProperty(Constants.OUT_FIELD_BODY) String body,
        @JsonProperty(Constants.OUT_FIELD_TIMESTAMP) String timeStamp,
        @JsonProperty(Constants.OUT_FIELD_RECALL_ID) String recallId) {
    
    private static String b(String s) {
        return s == null ? "" : s;
    }
    
    public String asTsv() {
        return "%s\t%s\t%s\t%s\t%s\t%s\n".formatted(b(timeStamp), b(sender), b(recipient), b(recallId), b(body));
    }
}
       