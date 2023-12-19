package it.zwets.sms.gateway.util;

import java.time.Instant;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import it.zwets.sms.gateway.SmsGatewayConfiguration.Constants;
import it.zwets.sms.gateway.dto.SendSmsRequest;

/**
 * Processes incoming requests.
 * 
 * When the <code>process</code> method has completed, either smsStatus is set
 * and we are done, and can (if clientId and correlId are known) send a response,
 * or just stop processing.
 * 
 * The error text may be set (and will be set if status is INVALID), or it will
 * be unset and status is either EXPIRED (which is not an error) or unset.
 * 
 * When unset, the in body has been replaced by the SendSmsRequest object.
 */
@Component
public class RequestValidator implements Processor {
    
    private static final Logger LOG = LoggerFactory.getLogger(RequestValidator.class);

    @Override
    public void process(Exchange exchange) {
        LOG.trace("Validating request: {}", exchange.getIn().getBody());
        
        SendSmsRequest req;
        
        try {
            ObjectMapper mapper = new ObjectMapper();
            req = mapper.readValue(exchange.getIn().getBody(String.class), SendSmsRequest.class);
            
            if (req.clientId() == null) {
                exchange.setProperty(Constants.OUT_FIELD_ERROR_TEXT, "Request lacks Client ID field");
            }
            else if (req.correlId() == null) {
                exchange.setProperty(Constants.OUT_FIELD_ERROR_TEXT, "Request lacks Correlation ID field");
            }
            else { // we have enough to produce a response
                exchange.setProperty(Constants.OUT_FIELD_CLIENT_ID, req.clientId());
                exchange.setProperty(Constants.OUT_FIELD_CORREL_ID, req.correlId());
                if (req.payload() == null) {
                    exchange.setProperty(Constants.OUT_FIELD_ERROR_TEXT, "Request lacks Payload field");
                }
                else if (req.deadline() == null) {
                    exchange.setProperty(Constants.OUT_FIELD_ERROR_TEXT, "Request lacks Deadline field");
                }
                else {
                    try {
                        Instant deadline = Instant.parse(req.deadline());
                        if (deadline.isBefore(Instant.now())) {
                            LOG.error("Request has expired: {}:{}", req.clientId(), req.correlId());
                            exchange.setProperty(Constants.OUT_FIELD_SMS_STATUS, Constants.SMS_STATUS_EXPIRED);
                        }
                        else {
                            LOG.debug("Request has passed validation, replacing body");
                            exchange.getIn().setBody(req);
                        }
                    }
                    catch (Exception e) {
                        exchange.setProperty(Constants.OUT_FIELD_ERROR_TEXT, "Invalid deadline value: %s".formatted(req.deadline()));
                    }
                }
            }
        } catch (JsonProcessingException e) {
            exchange.setProperty(Constants.OUT_FIELD_ERROR_TEXT, "Failed to parse request JSON");
        }
        
        if (exchange.getProperty(Constants.OUT_FIELD_ERROR_TEXT) != null) {
            LOG.error("Request failed validation: {}", exchange.getProperty(Constants.OUT_FIELD_ERROR_TEXT));
            exchange.setProperty(Constants.OUT_FIELD_SMS_STATUS, Constants.SMS_STATUS_INVALID);
        }
    }
}
