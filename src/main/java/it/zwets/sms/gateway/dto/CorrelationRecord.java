package it.zwets.sms.gateway.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import it.zwets.sms.gateway.SmsGatewayConfiguration.Constants;

@JsonInclude(Include.ALWAYS)
public record CorrelationRecord(
        @JsonProperty(Constants.OUT_FIELD_RECALL_ID) String recallId,
        @JsonProperty(Constants.OUT_FIELD_CLIENT_ID) String clientId,
        @JsonProperty(Constants.OUT_FIELD_CORREL_ID) String correlId) {
}
       