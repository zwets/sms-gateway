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
import org.apache.camel.component.smpp.SmppConstants;
import org.apache.camel.component.smpp.SmppSubmitSmCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import it.zwets.sms.gateway.dto.SmsMessage;

/**
 * Produces backend request for the SMPP gateway.
 * 
 * Transforms the in body from {@link SmsMessage} to a text body, and
 * sets the exchange headers needed for SMPP (see {@link SmppSubmitSmCommand}).
 * 
 * When the <code>process</code> method has completed, the message header
 * sms-status will be either unset the message can be processed, or INVALID
 * and error will be set.
 *
 * Does nothing if sms-status is already set on entry.
 */
public class SmppRequestProducer implements Processor {
    
    private static final Logger LOG = LoggerFactory.getLogger(SmppRequestProducer.class);
    private static final Pattern RECIPIENT_REGEX = Pattern.compile("^\\+255\\d{9}$");
    private static final Pattern SENDER_REGEX = Pattern.compile("^.{1,11}$");

    public SmppRequestProducer() {
        LOG.debug("Constructing SmppRequestProducer");
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        
        Message msg = exchange.getIn();

        if (msg.getHeader(HEADER_SMS_STATUS) != null) {
            LOG.debug("skipping, status already {}", msg.getHeader(HEADER_SMS_STATUS));
        }
        else {
            SmsMessage sms = exchange.getIn().getBody(SmsMessage.class);
            LOG.debug("transform SMS to SMPP request: %s".formatted(sms));
            
            String recipient = sms.getHeader(SMS_HEADER_TO);
            String sender = sms.getHeader(SMS_HEADER_SENDER);
            String message = sms.getBody();
            
            if (recipient == null) {
                msg.setHeader(HEADER_ERROR_TEXT, "SMS lacks recipient");
            }
            else if (!recipient.startsWith("+255")) {
                msg.setHeader(HEADER_ERROR_TEXT, "Gateway disallows foreign SMS recipient: %s".formatted(recipient));
            }
            else if (!RECIPIENT_REGEX.matcher(recipient).matches()) {
                msg.setHeader(HEADER_ERROR_TEXT, "Recipient number invalid for SMPP backend: %s".formatted(recipient));
            }
            else if (sender == null) {
                msg.setHeader(HEADER_ERROR_TEXT, "SMPP backend requires a message sender");
            }
            else if (!SENDER_REGEX.matcher(sender).matches()) {
                msg.setHeader(HEADER_ERROR_TEXT, "SMS sender does not have a 1-11 character length: %s".formatted(sender));
            }
            else if (message == null || message.isBlank()) {
                msg.setHeader(HEADER_ERROR_TEXT, "SMS message is empty");
            }
            else {
                // 0: Unknown 1: International 2: National 3: Network Specific 4: Subscriber Number 5: Alphanumeric 6: Abbreviated.
                msg.setHeader(SmppConstants.SOURCE_ADDR_TON, 5);
                msg.setHeader(SmppConstants.SOURCE_ADDR, sender.substring(1));
                // 0: Unknown 1: International 2: National 3: Network Specific 4: Subscriber Number 5: Alphanumeric 6: Abbreviated.
                msg.setHeader(SmppConstants.DEST_ADDR_TON, 1);
                msg.setHeader(SmppConstants.DEST_ADDR, recipient);
                // msg.setHeader(SmppConstants.VALIDITY_PERIOD, ???);
                msg.setBody(message);
            }
            
            if (msg.getHeader(HEADER_ERROR_TEXT) != null) {
                LOG.error("Failed to produce SMPP request: {}", msg.getHeader(HEADER_ERROR_TEXT));
                msg.setHeader(HEADER_SMS_STATUS, SMS_STATUS_INVALID);
            }
        }
    }
}
