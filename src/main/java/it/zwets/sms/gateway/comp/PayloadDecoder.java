package it.zwets.sms.gateway.comp;

import static it.zwets.sms.gateway.SmsGatewayConfiguration.Constants.HEADER_CLIENT_ID;
import static it.zwets.sms.gateway.SmsGatewayConfiguration.Constants.HEADER_ERROR_TEXT;
import static it.zwets.sms.gateway.SmsGatewayConfiguration.Constants.HEADER_SMS_STATUS;
import static it.zwets.sms.gateway.SmsGatewayConfiguration.Constants.SMS_HEADER_SENDER;
import static it.zwets.sms.gateway.SmsGatewayConfiguration.Constants.SMS_HEADER_TO;
import static it.zwets.sms.gateway.SmsGatewayConfiguration.Constants.SMS_STATUS_INVALID;

import java.util.Base64;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import it.zwets.sms.crypto.Vault;
import it.zwets.sms.gateway.dto.SendSmsRequest;
import it.zwets.sms.gateway.dto.SmsMessage;

/**
 * Decodes the payload of the incoming request.
 * 
 * Replaces the SendSmsRequest body by an {@link SmsMessage}, by decrypting
 * the payload and deserialising it into an {@link SmsMessage}.
 * 
 * When the <code>process</code> method has completed, either the in body
 * is now a valid {@link SmsMessage}, or it is unchanged and the header
 * sms-status is set to INVALID and the error-text will be set.
 * 
 * Does nothing if sms-status is already set on entry.
 */
public class PayloadDecoder implements Processor {
    
    private static final Logger LOG = LoggerFactory.getLogger(PayloadDecoder.class);
    private final Vault vault;

    public PayloadDecoder(Vault vault) {
        this.vault = vault;
    }
    
    @Override
    public void process(Exchange exchange) {
        
        Message msg = exchange.getIn();

        if (msg.getHeader(HEADER_SMS_STATUS) != null) {
            LOG.debug("skipping, already has status {}", msg.getHeader(HEADER_SMS_STATUS));
        }
        else {
            try {
                SendSmsRequest req = msg.getBody(SendSmsRequest.class);                
                SmsMessage sms = new SmsMessage();
                String clientId = msg.getHeader(HEADER_CLIENT_ID, String.class);
                
                LOG.trace("Decode and decrypt payload: {}", req.payload());
                byte[] bytes = Base64.getDecoder().decode(req.payload());
                sms.read(vault.decrypt(clientId, bytes));
    
                if (!sms.hasHeader(SMS_HEADER_TO)) {
                    msg.setHeader(HEADER_ERROR_TEXT, "SMS lacks field: %s".formatted(SMS_HEADER_TO));
                }
                else if (!sms.hasHeader(SMS_HEADER_SENDER)) {
                    msg.setHeader(HEADER_ERROR_TEXT, "SMS lacks field: %s".formatted(SMS_HEADER_SENDER));
                }
                else if (sms.getBody() == null || sms.getBody().length() == 0) {
                    msg.setHeader(HEADER_ERROR_TEXT, "SMS body is empty");
                }
                else { 
                    LOG.debug("Payload decoded, replacing body with SMS message");
                    msg.setBody(sms);
                }
            } catch (Exception e) {
                msg.setHeader(HEADER_ERROR_TEXT, "Failed to parse request payload: %s".formatted(e.getMessage()));
            }
            
            if (msg.getHeader(HEADER_ERROR_TEXT) != null) {
                LOG.error("Payload failed to decode: {}", msg.getHeader(HEADER_ERROR_TEXT));
                msg.setHeader(HEADER_SMS_STATUS, SMS_STATUS_INVALID);
            }
        }
    }
}
