package it.zwets.sms.gateway.routes;

import static it.zwets.sms.gateway.SmsGatewayConfiguration.Constants.HEADER_ERROR_TEXT;
import static it.zwets.sms.gateway.SmsGatewayConfiguration.Constants.HEADER_RECALL_ID;
import static it.zwets.sms.gateway.SmsGatewayConfiguration.Constants.HEADER_SMS_STATUS;
import static it.zwets.sms.gateway.SmsGatewayConfiguration.Constants.SMS_STATUS_FAILED;
import static it.zwets.sms.gateway.SmsGatewayConfiguration.Constants.SMS_STATUS_SENT;
import static org.apache.camel.LoggingLevel.DEBUG;
import static org.apache.camel.LoggingLevel.ERROR;
import static org.apache.camel.LoggingLevel.INFO;
import static org.apache.camel.LoggingLevel.TRACE;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.smpp.SmppConstants;
import org.apache.camel.component.smpp.SmppException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import it.zwets.sms.gateway.comp.SmppRequestProducer;
import it.zwets.sms.gateway.comp.SmppInboundProcessor;

/**
 * Camel route to the SMPP backend (SMSC). * 
 */
@Component
public class SmppRoute extends RouteBuilder {

    private static final Logger LOG = LoggerFactory.getLogger(SmppRoute.class);

    public static String SMPP_ROUTE = "direct:smpp-route";

    private static final String SMPP_INBOUND = "direct:smpp-inbound";
    
    @Autowired
    private SmppRequestProducer smppRequestProducer;

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
        
        // Outbound Route

        from(SMPP_ROUTE).routeId("smpp-route")

            // We send FAILED response when the Camel SMPP throws an exception, assuming this means
            // that the SMS did not go out.  Note: there may be a nested JSMPP NegativeResponseException,
            // whose getCommandStatus() gives detailed code as per SMPP specification 3.4, section 5.1.3.

            .onException(SmppException.class)
                .log(ERROR, LOG, "Send FAILED on exception from SMPP backend: ${exception} (cause: ${exception.cause}) ${exception.stacktrace}")
                .handled(true)
                .setHeader(HEADER_SMS_STATUS, constant(SMS_STATUS_FAILED))
                .setHeader(HEADER_ERROR_TEXT, simple("Failed to send SMS through SMSC"))
                .to(SmsRouter.RESPOND)
            .end()
               
            // On all other exceptions we assume the message MAY have been sent so we do not respond
            
            .onException(Throwable.class)
                .log(ERROR, LOG, "Exception from SMPP backend: ${exception} ${exception.stacktrace}")
                .log(ERROR, LOG, "NOT sending a FAILED response (we don't know if the SMS was sent)")
                .handled(true)
                .stop()
            .end()

            // Do the actual work
            
            .log(DEBUG, LOG, "Entering the SMPP route")
            .process(smppRequestProducer)
            .choice()
                .when(header(HEADER_SMS_STATUS).isNotNull())
                    .to(SmsRouter.RESPOND)
                .otherwise()
                    .setHeader(SmppConstants.SYSTEM_ID, constant(username))
                    .setHeader(SmppConstants.PASSWORD, constant(password))
                    .log(DEBUG, LOG, "Submitting SMS to SMSC")
                    .to(smppUri) // throws unless successful
                    .setHeader(HEADER_SMS_STATUS, constant(SMS_STATUS_SENT))
                    .setHeader(HEADER_RECALL_ID, regexReplaceAll(header(SmppConstants.ID), "^.*,", "")) // only last of list
                    .log(INFO, LOG, "SMS was submitted, recall ID ${header.%s}".formatted(HEADER_RECALL_ID))
                    .to(SmsRouter.RESPOND);
            
        // We have a TRX message centre and bind this route is by the messageReceiverRouteId

        from(SMPP_INBOUND).routeId(SMPP_INBOUND)
            .log(TRACE, LOG, "SMPP inbound message: ${body}")
            .process(smppInboundProcessor)
            .to(SmsRouter.RESPOND);

    }
}
