package it.zwets.sms.gateway.routes;

import static it.zwets.sms.gateway.SmsGatewayConfiguration.Constants.HEADER_ERROR_TEXT;
import static it.zwets.sms.gateway.SmsGatewayConfiguration.Constants.HEADER_SMS_STATUS;
import static it.zwets.sms.gateway.SmsGatewayConfiguration.Constants.SMS_STATUS_FAILED;
import static org.apache.camel.LoggingLevel.DEBUG;
import static org.apache.camel.LoggingLevel.TRACE;

import java.net.SocketException;
import java.net.UnknownHostException;

import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.hc.client5.http.ConnectTimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import it.zwets.sms.gateway.comp.VodaWaspRequestProducer;
import it.zwets.sms.gateway.comp.VodaWaspResponseProcessor;
import it.zwets.sms.gateway.dto.VodaWaspResponse;

/**
 * Camel route to the Vodacom Wasp REST API.
 * 
 * This route is specific to our SMS provider.  Rather than SMPP it offers a
 * simple dedicated REST API on a number of https endpoints.
 * 
 * If you use the SMS Gateway code, then you will most likely need to replace
 * this route with something that suits your need.
 * 
 * We use failover over the endpoints offered by the provider.  The URLs are
 * not defined in this reposity but in our production application.properties.
 */
@Component
public class VodaWaspRoute extends RouteBuilder {

    private static final Logger LOG = LoggerFactory.getLogger(VodaWaspRoute.class);

    public static String VODA_WASP_ROUTE = "direct:voda-wasp";

    @Autowired
    private VodaWaspRequestProducer vodaRequestProducer;

    @Autowired
    private VodaWaspResponseProcessor vodaResponseProcessor;

    @Override
    public void configure() throws Exception {

        // Global exception handler, respond with FAILED status
        onException(Throwable.class).routeId("voda-exception")
            .log(LoggingLevel.ERROR, LOG, "Exception in Voda Route: ${exception}")
            .handled(true)
            .setHeader(HEADER_SMS_STATUS, constant(SMS_STATUS_FAILED))
            .setHeader(HEADER_ERROR_TEXT, simple("Exception while handling request: ${exception.message}"))
            .to(SmsRouter.RESPOND);

        // Entrypoint from the main router process

        from(VODA_WASP_ROUTE).routeId("voda-wasp")
            .log(DEBUG, LOG, "Entering voda-wasp route")
            .process(vodaRequestProducer)
            .choice()
                .when(header(HEADER_SMS_STATUS).isNotNull())
                    .to(SmsRouter.RESPOND)
                .otherwise()
                    .marshal().jacksonXml()
                    .to("direct:call-wasp");

        // Keep this route separate from the one above
        // for its specific exception handling

        from("direct:call-wasp").routeId("call-wasp")
            .log(TRACE, LOG, "Wasp request: ${body}")

            // We split out the route to the Wasp endpoints to give it its own error handling.
            //
            // Override the global error handler: unless we have a connection timeout or failure,
            // the exception thrown MAY have occurred AFTER we sent our request, so we should not
            // respond FAILED because we do not know if the SMS was sent.
            //
            // All we can do UNTIL WE HAVE THE API FOR CHECKING DELIVERY is to give no response
            // at all to the client (effectively the request ends on the dead letter queue).
            //
            // We make an exception (no pun) for ConnectTimeoutException and SocketException which
            // we know will occur BEFORE we have established the http connection.

            .onException( // Match the exceptions in the failover below
                    ConnectTimeoutException.class,
                    SocketException.class,
                    UnknownHostException.class)
                .log(LoggingLevel.ERROR, LOG, "Failed to connect to any Voda Wasp endpoint (last: ${exception})")
                .handled(true)
                .setHeader(HEADER_SMS_STATUS, constant(SMS_STATUS_FAILED))
                .setHeader(HEADER_ERROR_TEXT, simple("Failed to connect to any Voda Wasp endpoint"))
                .to(SmsRouter.RESPOND)
            .end()

            .onException(Throwable.class)
                .log(LoggingLevel.ERROR, LOG, "Voda Wasp call aborted: ${exception} ${exception.stacktrace}")
                .log(LoggingLevel.ERROR, LOG, "NOT sending a response (we don't know if it was sent)")
                .handled(true)
                .stop()
            .end()

            // We do sticky round robin fail-over: next request goes to last known good
            // See: https://camel.apache.org/components/4.0.x/eips/failover-eip.html

            .loadBalance().failover(7 /* one less than number of endpoints */, false, true, true,
                    ConnectTimeoutException.class,
                    SocketException.class,
                    UnknownHostException.class)
                .to("{{sms.gateway.vodacom.wasp.url.1}}")
                .to("{{sms.gateway.vodacom.wasp.url.2}}")
                .to("{{sms.gateway.vodacom.wasp.url.3}}")
                .to("{{sms.gateway.vodacom.wasp.url.4}}")
                .to("{{sms.gateway.vodacom.wasp.url.5}}")
                .to("{{sms.gateway.vodacom.wasp.url.6}}")
                .to("{{sms.gateway.vodacom.wasp.url.7}}")
                .to("{{sms.gateway.vodacom.wasp.url.8}}")
            .end()
            .log(DEBUG, LOG, "Wasp response: ${body}")
            .to("direct:wasp-response");

        // Keep this route separate from the one above
        // so it uses the global exception handling again

        from("direct:wasp-response")
            .unmarshal().jacksonXml(VodaWaspResponse.class)
            .process(vodaResponseProcessor)
            .to(SmsRouter.RESPOND);

    }
}
