package it.zwets.sms.gateway.comp;

import static it.zwets.sms.gateway.SmsGatewayConfiguration.Constants.HEADER_ERROR_TEXT;
import static it.zwets.sms.gateway.SmsGatewayConfiguration.Constants.HEADER_SMS_STATUS;
import static it.zwets.sms.gateway.SmsGatewayConfiguration.Constants.SMS_HEADER_SENDER;
import static it.zwets.sms.gateway.SmsGatewayConfiguration.Constants.SMS_HEADER_TO;
import static it.zwets.sms.gateway.SmsGatewayConfiguration.Constants.SMS_STATUS_INVALID;

import java.util.regex.Pattern;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import it.zwets.sms.gateway.dto.SmsMessage;
import it.zwets.sms.gateway.dto.VodaRequest;

/**
 * Produces backend request for the Vodacom Wasp REST.
 * 
 * Transforms the in body from {@link SmsMessage} to {@link VodaRequest}
 * 
 * When the <code>process</code> method has completed, the message header
 * sms-status will be either unset and the message body has been replaced by
 * a {@link VodaRequest}, or INVALID and error will be set.
 *
 * Does nothing if sms-status is already set on entry.
 */
public class VodaRequestProducer implements Processor {
    
    private static final Logger LOG = LoggerFactory.getLogger(VodaRequestProducer.class);
    private static final Pattern RECIPIENT_REGEX = Pattern.compile("^255\\d{9}$");
    private static final Pattern SENDER_REGEX = Pattern.compile("^.{0,11}$");

    private final String username;
    private final String password;
    
    public VodaRequestProducer(String username, String password) {
        LOG.debug("Constructing with {}:{}", username, password);

        this.username = username;
        this.password = password;
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        
        Message msg = exchange.getIn();

        if (msg.getHeader(HEADER_SMS_STATUS) != null) {
            LOG.debug("skipping, status already {}", msg.getHeader(HEADER_SMS_STATUS));
        }
        else {
            SmsMessage sms = exchange.getIn().getBody(SmsMessage.class);
            LOG.debug("transform SMS to VodaRequest: %s".formatted(sms));
            
            String recipient = sms.getHeader(SMS_HEADER_TO);
            String sender = sms.getHeader(SMS_HEADER_SENDER);
            String message = sms.getBody();
            
            if (recipient == null) {
                msg.setHeader(HEADER_ERROR_TEXT, "SMS lacks recipient");
            }
            else if (!RECIPIENT_REGEX.matcher(recipient).matches()) {
                msg.setHeader(HEADER_ERROR_TEXT, "SMS recipient format invalid: %s".formatted(recipient));
            }
            else if (sender == null) { 
                msg.setHeader(HEADER_ERROR_TEXT, "SMS lacks sender");
            }
            else if (!SENDER_REGEX.matcher(sender).matches()) {
                msg.setHeader(HEADER_ERROR_TEXT, "SMS sender format invalid: %s".formatted(sender));
            }
            else if (message == null || message.isBlank()) {
                msg.setHeader(HEADER_ERROR_TEXT, "SMS message is empty");
            }
            else {
                VodaRequest vodaReq = new VodaRequest(username, password, sender, recipient, message);
                LOG.debug("Setting body to VodaRequest: %s".formatted(vodaReq));
                msg.setBody(vodaReq);
            }
            
            if (msg.getHeader(HEADER_ERROR_TEXT) != null) {
                LOG.error("Failed to produce VodaRequest: {}", msg.getHeader(HEADER_ERROR_TEXT));
                msg.setHeader(HEADER_SMS_STATUS, SMS_STATUS_INVALID);
            }
        }
    }
}
