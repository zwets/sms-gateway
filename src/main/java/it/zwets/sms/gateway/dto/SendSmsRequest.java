package it.zwets.sms.gateway.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import it.zwets.sms.gateway.SmsGatewayConfiguration.Constants;

@JsonInclude(Include.NON_NULL)
public record SendSmsRequest(
    @JsonProperty(Constants.IN_FIELD_CLIENT_ID) String clientId,
    @JsonProperty(Constants.IN_FIELD_CORREL_ID) String correlId,
    @JsonProperty(Constants.IN_FIELD_DEADLINE) String deadline,
    @JsonProperty(Constants.IN_FIELD_MESSAGE) String message) { }
