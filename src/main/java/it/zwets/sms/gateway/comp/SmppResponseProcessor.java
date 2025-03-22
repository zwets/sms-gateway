package it.zwets.sms.gateway.comp;

import static it.zwets.sms.gateway.SmsGatewayConfiguration.Constants.HEADER_CLIENT_ID;
import static it.zwets.sms.gateway.SmsGatewayConfiguration.Constants.HEADER_CORREL_ID;
import static it.zwets.sms.gateway.SmsGatewayConfiguration.Constants.HEADER_CORREL_REC;
import static it.zwets.sms.gateway.SmsGatewayConfiguration.Constants.HEADER_ERROR_TEXT;
import static it.zwets.sms.gateway.SmsGatewayConfiguration.Constants.HEADER_RECALL_ID;
import static it.zwets.sms.gateway.SmsGatewayConfiguration.Constants.HEADER_SMS_STATUS;
import static it.zwets.sms.gateway.SmsGatewayConfiguration.Constants.SMS_STATUS_FAILED;
import static it.zwets.sms.gateway.SmsGatewayConfiguration.Constants.SMS_STATUS_SENT;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.component.smpp.SmppConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import it.zwets.sms.gateway.dto.CorrelationRecord;


/**
 * Processes response from SMPP gateway to our submit request.
 * 
 * The message we process is the exchange that just went through the send,
 * enriched with a message ID (list because it may have been split).
 * 
 * We assume the submission was successful as Camel SMPP would have otherwise
 * thrown an exception.
 * 
 * We add headers to the exchange that the SMS route will translate to a
 * response to the client.  Notably we create a correlation record that will
 * be recorded so that we can correlate subsequent asynchronous notifies.
 * 
 * When the <code>process</code> method has completed, the message header
 * sms-status is guaranteed to be set.  Does nothing if sms-status is
 * already set on entry.
 */
@Component
public class SmppResponseProcessor implements Processor {
    
    private static final Logger LOG = LoggerFactory.getLogger(SmppResponseProcessor.class);
    
    @Override
    public void process(Exchange exchange) throws Exception {
        
        Message msg = exchange.getIn();

        if (msg.getHeader(HEADER_SMS_STATUS) != null) {
            LOG.debug("Skipping, status already {}", msg.getHeader(HEADER_SMS_STATUS));
        }
        else {

            try {
                LOG.debug("Processing SMPP response");
                
                Integer sentCount = msg.getHeader(SmppConstants.SENT_MESSAGE_COUNT, Integer.class);
                
                if (sentCount == null || sentCount == 0) {
                    throw new Exception("SMPP reported sent message count %s".formatted(sentCount));
                }
      
                String[] recallIds = msg.getHeader(SmppConstants.ID, String[].class);
                
                if (recallIds != null && recallIds.length > 0) {
                    String recallId = recallIds[recallIds.length - 1];
                    
                    LOG.debug("SMS was SENT with recall-id {}", recallId);
                    
                    // HACK: it seems Camel SMPP or JSMPP or the JSMPP SMSC simulator (and perhaps
                    // not the Voda SMSC) returns the message IDs as stringified HEX integers, whereas
                    // the delivery confirmations come with their DECIMAL equivalent.
                    //
                    // So what we do is try to parse them as hex integer, then store the real integer.
                    // When this goes wrong is if they are actually DECIMAL integers (which parse as
                    // hexadecimal too, of course, but then give a different number ...).
                    //
                    // For now let's try this.
                    
                    try {
                        Integer parsedRecallId = Integer.valueOf(recallId, 16);
                        LOG.debug("Hack applied: recallId {} will be interpreted as {}", recallId, parsedRecallId);
                        recallId = String.valueOf(parsedRecallId);
                    }
                    catch (NumberFormatException nfe) {
                        LOG.warn("Failed to parse recall ID as a hex number; hack needs fixing");
                    }

                    msg.setHeader(HEADER_RECALL_ID, recallId);
                    msg.setHeader(HEADER_CORREL_REC, new CorrelationRecord(
                        recallId, msg.getHeader(HEADER_CLIENT_ID, String.class), msg.getHeader(HEADER_CORREL_ID, String.class)
                    ));
                }
                else {
                    LOG.error("SMS was sent but we did not receive a recall ID to correlate on");
                }                  
                
                msg.setHeader(HEADER_SMS_STATUS, SMS_STATUS_SENT);
            }
            catch (Exception e) {
                LOG.error("Exception while processing SMPP response: {}", e.getMessage());
                msg.setHeader(HEADER_SMS_STATUS, SMS_STATUS_FAILED);
                msg.setHeader(HEADER_ERROR_TEXT, e.getMessage());
            }
        }
    }
}
