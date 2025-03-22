package it.zwets.sms.gateway.comp;

import static it.zwets.sms.gateway.SmsGatewayConfiguration.Constants.HEADER_CLIENT_ID;
import static it.zwets.sms.gateway.SmsGatewayConfiguration.Constants.HEADER_CORREL_ID;
import static it.zwets.sms.gateway.SmsGatewayConfiguration.Constants.HEADER_ERROR_TEXT;
import static it.zwets.sms.gateway.SmsGatewayConfiguration.Constants.HEADER_RECALL_ID;
import static it.zwets.sms.gateway.SmsGatewayConfiguration.Constants.HEADER_SMS_STATUS;
import static it.zwets.sms.gateway.SmsGatewayConfiguration.Constants.HEADER_TIMESTAMP;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import it.zwets.sms.gateway.dto.CorrelationRecord;
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

    @Autowired
    private CorrelationTable correlationTable;
    
    public void process(Exchange exchange) throws Exception {
        
        Message msg = exchange.getIn();
        
        String clientId = msg.getHeader(HEADER_CLIENT_ID, String.class);
        String correlId = msg.getHeader(HEADER_CORREL_ID, String.class);
        String timeStamp = msg.getHeader(HEADER_TIMESTAMP, Instant.now(), Instant.class).truncatedTo(ChronoUnit.SECONDS).toString();
        String smsStatus = msg.getHeader(HEADER_SMS_STATUS, String.class);
        String recallId = msg.getHeader(HEADER_RECALL_ID, String.class);
        String errorText = msg.getHeader(HEADER_ERROR_TEXT, String.class);

        // If we don't have clientId and correlId but we do have recallId then
        // the correlation table should hopefully have the mapping or receive
        // it very soon (race condition: we can get here before correlation
        // route has picked it up from the Kafka topic and added it to table).
        
        if (smsStatus == null) {
            LOG.warn("Not producing response: SMS_STATUS header is not set");
        }
        else if (correlId == null || clientId == null) {
            
            if (recallId == null) {
                LOG.error("No recall-id and no correl-id or client-id, no response will be sent to client");
            }
            else {
                LOG.debug("Retrieving correl-id and client-id for recall-id {}", recallId);
                
                CorrelationRecord rec = correlationTable.fetch(recallId);
        
                if (rec == null) {
                    LOG.debug("No correlation record found for recall ID {}, retrying in one second", recallId);
                    Thread.sleep(1000);
                    rec = correlationTable.fetch(recallId);
                }
                
                if (rec != null) {
                    LOG.debug("Found correlation record {} -> {}:{}", rec.recallId(), rec.clientId(), rec.correlId());
                    clientId = rec.clientId();
                    correlId = rec.correlId();
                }
                else {
                    LOG.error("No correlation record found for recall ID {}, no response will be sent to client", recallId);
                }
            }
        }

        // Make sure we return sane response
        
        if (correlId != null && clientId != null && smsStatus != null) {
            LOG.debug("Producing response: {}:{}:{}:{}:{}:{}", clientId, correlId, timeStamp, smsStatus, recallId, errorText);
            msg.setBody(new SmsStatusResponse(clientId, correlId, timeStamp, smsStatus, recallId, errorText));
        }
    }
}
