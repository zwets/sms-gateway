package it.zwets.sms.gateway.routes;

import static it.zwets.sms.gateway.SmsGatewayConfiguration.Constants.HEADER_ERROR_TEXT;
import static it.zwets.sms.gateway.SmsGatewayConfiguration.Constants.HEADER_SMS_STATUS;
import static it.zwets.sms.gateway.SmsGatewayConfiguration.Constants.SMS_STATUS_FAILED;
import static org.apache.camel.LoggingLevel.DEBUG;

import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.hc.client5.http.ConnectTimeoutException;
import org.apache.hc.client5.http.HttpHostConnectException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import it.zwets.sms.gateway.comp.VodaRequestProducer;
import it.zwets.sms.gateway.comp.VodaResponseProcessor;
import it.zwets.sms.gateway.dto.VodaResponse;

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
    
    private static final Logger LOG = LoggerFactory.getLogger(TestClientRoute.class);
    
    public static String VODA_WASP_ROUTE = "direct:voda-wasp";

    @Autowired
    private VodaRequestProducer vodaRequestProducer;
    
    @Autowired
    private VodaResponseProcessor vodaResponseProcessor;

    @Override
    public void configure() throws Exception {

        // Global exception handler, respond with FAILED status
        // See: https://camel.apache.org/manual/exception-clause.html
        onException(Throwable.class).routeId("voda-exception")
            .log(LoggingLevel.ERROR, LOG, "Exception in Voda Route: ${exception}: ${exception.stacktrace}")
//            .logExhausted(true)
//            .retriesExhaustedLogLevel(LoggingLevel.ERROR)
            .handled(true)
            .setHeader(HEADER_SMS_STATUS, constant(SMS_STATUS_FAILED))
            .setHeader(HEADER_ERROR_TEXT, simple("Exception while handling request: ${exception.message}"))
            .to(SmsRouter.RESPOND);
        
        from(VODA_WASP_ROUTE).routeId("voda-wasp")
            .log(DEBUG, LOG, "Entering voda-wasp route")
            .process(vodaRequestProducer)
            .choice()
                .when(header(HEADER_SMS_STATUS).isNotNull())
                    .to(SmsRouter.RESPOND)
                .otherwise()
                    .marshal().jacksonXml()
                    // We do sticky fail-over: next request goes to last known good endpoint
                    // See: https://camel.apache.org/components/4.0.x/eips/failover-eip.html
                    .loadBalance().failover(-1, false, false, true, ConnectTimeoutException.class, HttpHostConnectException.class)
                        .to("{{sms.gateway.vodacom.wasp.url.1}}")
                        .to("{{sms.gateway.vodacom.wasp.url.2}}")
                        .to("{{sms.gateway.vodacom.wasp.url.3}}")
                        .to("{{sms.gateway.vodacom.wasp.url.4}}")
//                        .to("{{sms.gateway.vodacom.wasp.url.5}}")
//                        .to("{{sms.gateway.vodacom.wasp.url.6}}")
//                        .to("{{sms.gateway.vodacom.wasp.url.7}}")
//                        .to("{{sms.gateway.vodacom.wasp.url.8}}")
                    .end()
                    .log(DEBUG, LOG, "Backend response: ${body}")
                    .unmarshal().jacksonXml(VodaResponse.class)
                    .process(vodaResponseProcessor)
                    .to(SmsRouter.RESPOND);
    }
}
