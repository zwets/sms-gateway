package it.zwets.sms.gateway.comp;

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
import it.zwets.sms.gateway.dto.VodaResponse;

/**
 * Processes response from Voda Wasp REST API backend.
 * 
 * Transforms the in body froma {@link VodaResponse} to message headers.
 * 
 * When the <code>process</code> method has completed, the message header
 * sms-status is guaranteed to be set.
 *
 * Does nothing if sms-status is already set on entry.
 */
public class VodaResponseProcessor implements Processor {
    
    private static final Logger LOG = LoggerFactory.getLogger(VodaResponseProcessor.class);

    @Override
    public void process(Exchange exchange) throws Exception {
        
        Message msg = exchange.getIn();

        if (msg.getHeader(HEADER_SMS_STATUS) == null) {
            LOG.debug("skipping, status already {}", msg.getHeader(HEADER_SMS_STATUS));
        }
        else {

            try {
                LOG.debug("processing Vodacom response: {}", msg.getBody());
                VodaResponse vodaRsp = msg.getBody(VodaResponse.class);
                
                if (vodaRsp.Status.StatusCode() == 0) {
                    msg.setHeader(HEADER_SMS_STATUS, Constants.SMS_STATUS_SENT);
                    msg.setHeader(HEADER_RECALL_ID, vodaRsp.MessageID);
                }
                else {
                    LOG.error("Error response from Vodacom: {}", vodaRsp.toString());
                    msg.setHeader(HEADER_SMS_STATUS, SMS_STATUS_FAILED);
                    msg.setHeader(HEADER_ERROR_TEXT, "Error {} from Vodacom: {}: {}"
                            .formatted(vodaRsp.Status.StatusCode(), vodaRsp.Status.StatusText(), vodaRsp.Status.Detail()));
                }
            }
            catch (Exception e) {
                LOG.error("Exception while processing VodaRequest: {}", e.getMessage());
                msg.setHeader(HEADER_SMS_STATUS, SMS_STATUS_FAILED);
                msg.setHeader(HEADER_ERROR_TEXT, e.getMessage());
            }
        }
    }
}
