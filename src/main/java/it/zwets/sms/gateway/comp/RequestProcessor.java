package it.zwets.sms.gateway.comp;

import static it.zwets.sms.gateway.SmsGatewayConfiguration.Constants.HEADER_CLIENT_ID;
import static it.zwets.sms.gateway.SmsGatewayConfiguration.Constants.HEADER_CORREL_ID;
import static it.zwets.sms.gateway.SmsGatewayConfiguration.Constants.HEADER_ERROR_TEXT;
import static it.zwets.sms.gateway.SmsGatewayConfiguration.Constants.HEADER_SMS_STATUS;
import static it.zwets.sms.gateway.SmsGatewayConfiguration.Constants.SMS_STATUS_EXPIRED;
import static it.zwets.sms.gateway.SmsGatewayConfiguration.Constants.SMS_STATUS_INVALID;

import java.time.Instant;
import java.util.Arrays;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import it.zwets.sms.gateway.dto.SendSmsRequest;

/**
 * Deserialises and validates incoming requests.
 * 
 * Transforms the incoming request string to a {@link SendSmsRequest}
 * and copies client-id and correl-id to message headers so these can
 * can eventually be copied to the reponse.
 * 
 * Upon completion of the <code>process</code> method, either the body
 * is a valid {@link SendSmsRequest}, or it is unchanged and the header 
 * sms-status will be set to either <code>INVALID</code> and
 * error-text will be set, or <code>EXPIRED</code> with no error-text.
 * 
 * Does nothing if the sms-status is already set on entry.
 */
public class RequestProcessor implements Processor {
    
    private static final Logger LOG = LoggerFactory.getLogger(RequestProcessor.class);
    
    private final String[] allowedClients;
    
    public RequestProcessor(String[] allowedClients) {
        LOG.debug("Constructing with allowed clients: {}", String.join(", ", allowedClients));
        this.allowedClients = allowedClients;
    }

    @Override
    public void process(Exchange exchange) {
        
        Message msg = exchange.getIn();
        
        if (msg.getHeader(HEADER_SMS_STATUS) != null) {
            LOG.debug("skipping, already has status {}", msg.getHeader(HEADER_SMS_STATUS));
        }
        else
        {
            LOG.trace("Validating request: {}", exchange.getIn().getBody());
            
            try {
                SendSmsRequest req = new ObjectMapper()
                        .readValue(exchange.getIn().getBody(String.class), SendSmsRequest.class);
                
                if (req.clientId() == null) {
                    msg.setHeader(HEADER_ERROR_TEXT, "Request lacks Client ID field");
                }
                else if (req.correlId() == null) {
                    msg.setHeader(HEADER_ERROR_TEXT, "Request lacks Correlation ID field");
                }
                else { // we have enough to produce a response
                    msg.setHeader(HEADER_CLIENT_ID, req.clientId());
                    msg.setHeader(HEADER_CORREL_ID, req.correlId());
                    
                    if (Arrays.stream(allowedClients).noneMatch(req.clientId()::equals)) {
                        msg.setHeader(HEADER_ERROR_TEXT, "Client ID is unknown or disallowed");
                    }
                    if (req.payload() == null) {
                        msg.setHeader(HEADER_ERROR_TEXT, "Request lacks Payload field");
                    }
                    else if (req.deadline() == null) {
                        msg.setHeader(HEADER_ERROR_TEXT, "Request lacks Deadline field");
                    }
                    else {
                        try {
                            Instant deadline = Instant.parse(req.deadline());
                            if (deadline.isBefore(Instant.now())) {
                                LOG.warn("Expired request: {}:{}", req.clientId(), req.correlId());
                                msg.setHeader(HEADER_SMS_STATUS, SMS_STATUS_EXPIRED);
                            }
                            else {
                                LOG.debug("Request has passed validation, replacing body");
                                msg.setBody(req);
                            }
                        }
                        catch (Exception e) {
                            msg.setHeader(HEADER_ERROR_TEXT, "Invalid deadline value: %s".formatted(req.deadline()));
                        }
                    }
                }
            } catch (JsonProcessingException e) {
                msg.setHeader(HEADER_ERROR_TEXT, "Failed to parse request JSON");
            }
        
            if (msg.getHeader(HEADER_ERROR_TEXT) != null) {
                LOG.error("Request failed validation: {}", msg.getHeader(HEADER_ERROR_TEXT));
                msg.setHeader(HEADER_SMS_STATUS, SMS_STATUS_INVALID);
            }
        }
    }
}
