package it.zwets.sms.gateway.comp;

import static it.zwets.sms.gateway.SmsGatewayConfiguration.Constants.HEADER_CLIENT_ID;
import static it.zwets.sms.gateway.SmsGatewayConfiguration.Constants.HEADER_CORREL_ID;
import static it.zwets.sms.gateway.SmsGatewayConfiguration.Constants.HEADER_CORREL_REC;
import static it.zwets.sms.gateway.SmsGatewayConfiguration.Constants.HEADER_ERROR_TEXT;
import static it.zwets.sms.gateway.SmsGatewayConfiguration.Constants.HEADER_RECALL_ID;
import static it.zwets.sms.gateway.SmsGatewayConfiguration.Constants.HEADER_SMS_STATUS;
import static it.zwets.sms.gateway.SmsGatewayConfiguration.Constants.SMS_STATUS_FAILED;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import it.zwets.sms.gateway.SmsGatewayConfiguration.Constants;
import it.zwets.sms.gateway.dto.CorrelationRecord;
import it.zwets.sms.gateway.dto.VodaWaspResponse;

/**
 * Processes response from Voda Wasp REST API backend.
 * 
 * Transforms the in body from a {@link VodaWaspResponse} to message headers.
 * 
 * When the <code>process</code> method has completed, the message header
 * sms-status is guaranteed to be set.
 *
 * Does nothing if sms-status is already set on entry.
 */
public class VodaWaspResponseProcessor implements Processor {
    
    private static final Logger LOG = LoggerFactory.getLogger(VodaWaspResponseProcessor.class);

    @Override
    public void process(Exchange exchange) throws Exception {
        
        Message msg = exchange.getIn();

        if (msg.getHeader(HEADER_SMS_STATUS) != null) {
            LOG.debug("skipping, status already {}", msg.getHeader(HEADER_SMS_STATUS));
        }
        else {

            try {
                LOG.debug("processing Vodacom response: {}", msg.getBody());
                VodaWaspResponse vodaRsp = msg.getBody(VodaWaspResponse.class);
                
                if (vodaRsp.Status.StatusCode() == 0) {
                    msg.setHeader(HEADER_SMS_STATUS, Constants.SMS_STATUS_SENT);
                    msg.setHeader(HEADER_RECALL_ID, vodaRsp.MessageID);
                    
                    // Add correlation record on header, will be recorded in table downstream
                    msg.setHeader(HEADER_CORREL_REC, 
                        new CorrelationRecord(
                            vodaRsp.MessageID,
                            msg.getHeader(HEADER_CLIENT_ID, String.class),
                            msg.getHeader(HEADER_CORREL_ID, String.class)));
                }
                else {
                    LOG.error("Error response from Vodacom: {}", vodaRsp.toString());
                    msg.setHeader(HEADER_SMS_STATUS, SMS_STATUS_FAILED);
                    msg.setHeader(HEADER_ERROR_TEXT, "Error {} from Vodacom: {}: {}"
                            .formatted(vodaRsp.Status.StatusCode(), vodaRsp.Status.StatusText(), vodaRsp.Status.Detail()));
                }
            }
            catch (Exception e) {
                LOG.error("Exception while processing VodaWaspRequest: {}", e.getMessage());
                msg.setHeader(HEADER_SMS_STATUS, SMS_STATUS_FAILED);
                msg.setHeader(HEADER_ERROR_TEXT, e.getMessage());
            }
        }
    }
}
