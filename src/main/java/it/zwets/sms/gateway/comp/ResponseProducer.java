package it.zwets.sms.gateway.comp;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import it.zwets.sms.gateway.SmsGatewayConfiguration.Constants;
import it.zwets.sms.gateway.dto.SmsStatusResponse;

/**
 * Produces the outbound response message.
 * 
 * The outbound response is a {@link SmsStatusResponse} objact put in the
 * in-body of the exchange, provided we have client-id and correl-id.
 * 
 * The message contents are copied from the OUT_FIELD exchange properties.
 * If we have no client-id or correl-id, the in-body is not touched.
 */
@Component
public class ResponseProducer implements Processor {
    
    private static final Logger LOG = LoggerFactory.getLogger(ResponseProducer.class);
    
    public void process(Exchange exchange) throws Exception {
        
        String correlId = exchange.getProperty(Constants.OUT_FIELD_CORREL_ID, String.class);
        String clientId = exchange.getProperty(Constants.OUT_FIELD_CLIENT_ID, String.class);
        String smsStatus = exchange.getProperty(Constants.OUT_FIELD_SMS_STATUS, String.class);
        String errorText = exchange.getProperty(Constants.OUT_FIELD_ERROR_TEXT, String.class);

            // Make sure we return sane response
        
        if (correlId != null && clientId != null && smsStatus != null) {
            LOG.debug("Producing response: {}:{}:{}:{}", correlId, clientId, smsStatus, errorText);
            exchange.getIn().setBody(new SmsStatusResponse(correlId, clientId, smsStatus, errorText));
        }
        else {
            LOG.debug("Not producing response: no client ID and correl ID present");
        }
    }
}
