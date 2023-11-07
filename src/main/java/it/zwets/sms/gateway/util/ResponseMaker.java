package it.zwets.sms.gateway.util;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import it.zwets.sms.gateway.SmsGatewayConfiguration.Constants;
import it.zwets.sms.gateway.dto.SmsStatusResponse;

@Component
public class ResponseMaker implements Processor {
    
    private static final Logger LOG = LoggerFactory.getLogger(ResponseMaker.class);
    
    @Override
    public void process(Exchange exchange) throws Exception {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Processing response {}:{}:{}",
                    exchange.getProperty(Constants.OUT_FIELD_CORREL_ID),
                    exchange.getProperty(Constants.OUT_FIELD_CLIENT_ID),
                    exchange.getProperty(Constants.OUT_FIELD_SMS_STATUS));
        }
        
        exchange.getIn().setBody(new SmsStatusResponse(
                exchange.getProperty(Constants.OUT_FIELD_CORREL_ID, String.class),
                exchange.getProperty(Constants.OUT_FIELD_CLIENT_ID, String.class),
                exchange.getProperty(Constants.OUT_FIELD_SMS_STATUS, String.class),
                exchange.getProperty(Constants.OUT_FIELD_ERROR_CODE, Integer.class),
                exchange.getProperty(Constants.OUT_FIELD_ERROR_TEXT, String.class)));
    }
}
