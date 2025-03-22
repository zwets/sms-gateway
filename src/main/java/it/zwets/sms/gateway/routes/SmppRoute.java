package it.zwets.sms.gateway.routes;

import static it.zwets.sms.gateway.SmsGatewayConfiguration.Constants.HEADER_ERROR_TEXT;
import static it.zwets.sms.gateway.SmsGatewayConfiguration.Constants.HEADER_RECALL_ID;
import static it.zwets.sms.gateway.SmsGatewayConfiguration.Constants.HEADER_SMS_STATUS;
import static it.zwets.sms.gateway.SmsGatewayConfiguration.Constants.SMS_STATUS_FAILED;
import static org.apache.camel.LoggingLevel.DEBUG;
import static org.apache.camel.LoggingLevel.ERROR;
import static org.apache.camel.LoggingLevel.INFO;
import static org.apache.camel.LoggingLevel.TRACE;

import java.io.IOException;

import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.smpp.SmppConstants;
import org.apache.camel.component.smpp.SmppException;
import org.jsmpp.extra.NegativeResponseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import it.zwets.sms.gateway.comp.SmppInboundProcessor;
import it.zwets.sms.gateway.comp.SmppRequestProducer;
import it.zwets.sms.gateway.comp.SmppResponseProcessor;

/**
 * Camel routes for submission to the SMPP backend (SMSC), and delivery notifications back. 
 */
@Component
public class SmppRoute extends RouteBuilder {

    private static final Logger LOG = LoggerFactory.getLogger(SmppRoute.class);

    public static String SMPP_SUBMIT = "direct:smpp-submit";

    private static final String SMPP_INBOUND = "direct:smpp-inbound";
    
    @Autowired
    private SmppRequestProducer smppRequestProducer;

    @Autowired
    private SmppResponseProcessor smppResponseProcessor;

    @Autowired
    private SmppInboundProcessor smppInboundProcessor;
    
    private final String smppUri;
    private final String username;
    private final String password;
    
    public SmppRoute(
            @Value("${sms.gateway.smpp.host}") String host,
            @Value("${sms.gateway.smpp.port}") int port,
            @Value("${sms.gateway.smpp.username}") String username,
            @Value("${sms.gateway.smpp.password}") String password)
    {
        LOG.info("SmppRoute created to {}:{}", host, port);
        smppUri = "smpp://%s:%s?messageReceiverRouteId=%s".formatted(host, port, SMPP_INBOUND);
        this.username = username;
        this.password = password;
    }

    @Override
    public void configure() throws Exception {
        
        // Global exception handler, overridden in the submit route to not always send FAILED.
        
        onException(Throwable.class).routeId("smpp-route-exception")
            .log(LoggingLevel.ERROR, LOG, "Exception in SMPP Route: ${exception}")
            .handled(true)
            .setHeader(HEADER_SMS_STATUS, constant(SMS_STATUS_FAILED))
            .setHeader(HEADER_ERROR_TEXT, simple("Exception while handling request: ${exception.message}"))
            .to(SmsRouter.RESPOND);

        // Outbound Route (we use it only for submitting but could be used for cancal too)
        
        from(SMPP_SUBMIT).routeId("smpp-submit")

            // Use route-local exception handling so we respond FAILED when we know for sure
            // that the send failed, but send no response if we don't know if it was sent.
        
            // The Camel SmppException and JSMPP NegativeResponseException indicate FAILED.
            // Note: the latter has getCommandStatus() with detailed code as per SMPP 
            // specification 3.4, section 5.1.3.  We log a stacktrace for now to help diag.

            .onException(
                    IOException.class,
                    SmppException.class,
                    NegativeResponseException.class)
                .log(ERROR, LOG, "Send FAILED on exception from SMPP backend: ${exception} (cause: ${exception.cause}) ${exception.stacktrace}")
                .handled(true)
                .setHeader(HEADER_SMS_STATUS, constant(SMS_STATUS_FAILED))
                .setHeader(HEADER_ERROR_TEXT, simple("Failed to send SMS through SMSC"))
                .to(SmsRouter.RESPOND)
            .end()
               
            // All other exceptions MAY mean the message was sent so we send no response
            
            .onException(Throwable.class)
                .log(ERROR, LOG, "Exception from SMPP backend: ${exception} ${exception.stacktrace}")
                .log(ERROR, LOG, "NOT sending a FAILED response (we don't know if the SMS was sent)")
                .handled(true)
                .stop()
            .end()

            // Do the actual work
            
            .log(DEBUG, LOG, "Entering the SMPP submit route")
            .process(smppRequestProducer)
            .choice()
                .when(header(HEADER_SMS_STATUS).isNotNull())
                    .to(SmsRouter.RESPOND)
                .otherwise()
                    .setHeader(SmppConstants.SYSTEM_ID, constant(username))
                    .setHeader(SmppConstants.PASSWORD, constant(password))
                    .log(DEBUG, LOG, "Submitting SMS to SMSC")
                    .to(smppUri) // throws unless successful
                    .process(smppResponseProcessor)
                    .log(INFO, LOG, "SMS was submitted, recall ID ${header.%s}".formatted(HEADER_RECALL_ID))
                    .to(SmsRouter.RESPOND);
            
        // Inbound route, set by messageReceiverRouteId to receive backend notifications.
        // Our SMSC supports TRX (response in the same session) and this is set on our side
        // by setting the messageReceiverRouteId to an inbound consumer.

        from(SMPP_INBOUND).routeId(SMPP_INBOUND)
            .log(TRACE, LOG, "SMPP inbound message: ${body}")
            .process(smppInboundProcessor)
            .to(SmsRouter.RESPOND);

    }
}
