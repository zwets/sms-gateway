package it.zwets.sms.gateway.util;

import org.apache.camel.Exchange;
import org.springframework.stereotype.Component;

import it.zwets.sms.gateway.SmsGatewayConfiguration.Constants;
import it.zwets.sms.gateway.dto.SmsStatusResponse;

@Component
public class ResponseMaker {
    
    public void produceStatus(Exchange exchange, String status) {
        exchange.getIn().setBody(new SmsStatusResponse(
                exchange.getProperty(Constants.IN_FIELD_CORREL_ID, String.class),
                exchange.getProperty(Constants.IN_FIELD_CLIENT_ID, String.class),
                status,
                0,
                null));
    }
    
    public void produceError(Exchange exchange, String status, int code, String text) {
        exchange.getIn().setBody(new SmsStatusResponse(
                exchange.getProperty(Constants.IN_FIELD_CORREL_ID, String.class),
                exchange.getProperty(Constants.IN_FIELD_CLIENT_ID, String.class),
                status,
                code,
                text));
    }
    
    public void expired(Exchange exchange) {
        produceStatus(exchange, Constants.SMS_STATUS_EXPIRED);
    }

    public void relayed(Exchange exchange) {
        produceStatus(exchange, Constants.SMS_STATUS_RELAYED);
    }
    
    public void invalid(Exchange exchange, int code, String reason) {
        produceError(exchange, Constants.SMS_STATUS_INVALID, code, reason);
    }

}
