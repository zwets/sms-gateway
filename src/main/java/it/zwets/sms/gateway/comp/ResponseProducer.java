package it.zwets.sms.gateway.comp;

import static it.zwets.sms.gateway.SmsGatewayConfiguration.Constants.HEADER_CLIENT_ID;
import static it.zwets.sms.gateway.SmsGatewayConfiguration.Constants.HEADER_CORREL_ID;
import static it.zwets.sms.gateway.SmsGatewayConfiguration.Constants.HEADER_ERROR_TEXT;
import static it.zwets.sms.gateway.SmsGatewayConfiguration.Constants.HEADER_RECALL_ID;
import static it.zwets.sms.gateway.SmsGatewayConfiguration.Constants.HEADER_SMS_STATUS;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import it.zwets.sms.gateway.dto.SmsStatusResponse;

/**
 * Produces the outbound response message.
 * 
 * The outbound response is a {@link SmsStatusResponse} objact put in the
 * in-body of the exchange, provided we have client-id and correl-id.
 * 
 * The message contents are copied from the header fields set during routing.
 * If we have no client-id or correl-id, the in-body is not touched.
 */
@Component
public class ResponseProducer implements Processor {
    
    private static final Logger LOG = LoggerFactory.getLogger(ResponseProducer.class);
    
    public void process(Exchange exchange) throws Exception {
        
        Message msg = exchange.getIn();
        
        String clientId = msg.getHeader(HEADER_CLIENT_ID, String.class);
        String correlId = msg.getHeader(HEADER_CORREL_ID, String.class);
        String smsStatus = msg.getHeader(HEADER_SMS_STATUS, String.class);
        String recallId = msg.getHeader(HEADER_RECALL_ID, String.class);
        String errorText = msg.getHeader(HEADER_ERROR_TEXT, String.class);

            // Make sure we return sane response
        
        if (correlId != null && clientId != null && smsStatus != null) {
            LOG.debug("Producing response: {}:{}:{}:{}:{}", clientId, correlId, smsStatus, recallId, errorText);
            msg.setBody(new SmsStatusResponse(clientId, correlId, smsStatus, recallId, errorText));
        }
        else {
            LOG.warn("Not producing response: no client ID and correl ID present");
        }
    }
}
