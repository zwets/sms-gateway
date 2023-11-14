package it.zwets.sms.gateway.util;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import it.zwets.sms.gateway.SmsGatewayConfiguration.Constants;
import it.zwets.sms.gateway.dto.SmsStatusResponse;

public class ResponseMaker implements Processor {
    
    private static final Logger LOG = LoggerFactory.getLogger(ResponseMaker.class);
    
    public void process(Exchange exchange) throws Exception {
        
        String correlId = exchange.getProperty(Constants.OUT_FIELD_CORREL_ID, String.class);
        String clientId = exchange.getProperty(Constants.OUT_FIELD_CLIENT_ID, String.class);
        String smsStatus = exchange.getProperty(Constants.OUT_FIELD_SMS_STATUS, String.class);
        int errorCode = exchange.getProperty(Constants.OUT_FIELD_ERROR_CODE, 0, Integer.class);
        String errorText = exchange.getProperty(Constants.OUT_FIELD_ERROR_TEXT, String.class);

            // Make sure we return sane response
        
        if (correlId == null || clientId == null || smsStatus == null) {
            throw new RuntimeException("ResponseMaker: internal error: refusing to reponse with null field(s)");
        }
        
        LOG.trace("Generating response: {}:{}:{}:{}:{}", correlId, clientId, smsStatus, errorCode, errorText);
        exchange.getIn().setBody(new SmsStatusResponse(correlId, clientId, smsStatus, errorCode, errorText));
    }
}
