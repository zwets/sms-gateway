package it.zwets.sms.gateway.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import it.zwets.sms.gateway.SmsGatewayConfiguration.Constants;

@JsonInclude(Include.NON_EMPTY)
public record SmsStatusResponse(
        @JsonProperty(Constants.OUT_FIELD_CORREL_ID) String correlId,
        @JsonProperty(Constants.OUT_FIELD_CLIENT_ID) String clientId,
        @JsonProperty(Constants.OUT_FIELD_SMS_STATUS) String smsStatus,
        @JsonProperty(Constants.OUT_FIELD_ERROR_TEXT) String errorText) { }
       