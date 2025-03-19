package it.zwets.sms.gateway.routes;

import static it.zwets.sms.gateway.SmsGatewayConfiguration.Constants.HEADER_ERROR_TEXT;
import static it.zwets.sms.gateway.SmsGatewayConfiguration.Constants.HEADER_SMS_STATUS;
import static it.zwets.sms.gateway.SmsGatewayConfiguration.Constants.SMS_STATUS_FAILED;
import static org.apache.camel.LoggingLevel.DEBUG;
import static org.apache.camel.LoggingLevel.ERROR;
import static org.apache.camel.LoggingLevel.INFO;
import static org.apache.camel.LoggingLevel.TRACE;

import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.smpp.SmppConstants;
import org.apache.camel.component.smpp.SmppException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import it.zwets.sms.gateway.comp.SmppRequestProducer;
import it.zwets.sms.gateway.comp.SmppResponseProcessor;

/**
 * Camel route to the SMPP backend (SMSC). * 
 */
@Component
public class SmppRoute extends RouteBuilder {

    private static final Logger LOG = LoggerFactory.getLogger(SmppRoute.class);

    public static String SMPP_ROUTE = "direct:smpp-route";

    private static final String RESPOND = "direct:smpp-respond";
    
    @Autowired
    private SmppRequestProducer smppRequestProducer;

    @Autowired
    private SmppResponseProcessor smppResponseProcessor;
    
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
        smppUri = "smpp://%s:%s?messageReceiverRouteId=%s".formatted(host, port, RESPOND);
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
                    .log(TRACE, LOG, "SMPP outbound: ${body}")
                    .setHeader(SmppConstants.SYSTEM_ID, constant(username))
                    .setHeader(SmppConstants.PASSWORD, constant(password))
                    .to(smppUri)
                    .to(RESPOND);
            
        // We have a TRX message centre, so this response route is bound by the messageReceiverRouteId

        from(RESPOND).routeId(RESPOND)
            .log(DEBUG, LOG, "SMPP respond: ${body}")
            .process(smppResponseProcessor)
            .to(SmsRouter.RESPOND);

    }
}
