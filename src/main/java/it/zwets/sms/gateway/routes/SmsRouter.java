package it.zwets.sms.gateway.routes;

import static it.zwets.sms.gateway.SmsGatewayConfiguration.Constants.HEADER_CLIENT_ID;
import static it.zwets.sms.gateway.SmsGatewayConfiguration.Constants.HEADER_CORREL_ID;
import static it.zwets.sms.gateway.SmsGatewayConfiguration.Constants.HEADER_ERROR_TEXT;
import static it.zwets.sms.gateway.SmsGatewayConfiguration.Constants.HEADER_SMS_STATUS;
import static it.zwets.sms.gateway.SmsGatewayConfiguration.Constants.SMS_STATUS_FAILED;
import static it.zwets.sms.gateway.SmsGatewayConfiguration.Constants.SMS_STATUS_INVALID;

import org.apache.camel.Endpoint;
import org.apache.camel.EndpointInject;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import it.zwets.sms.gateway.SmsGatewayConfiguration.Constants;
import it.zwets.sms.gateway.comp.PayloadDecoder;
import it.zwets.sms.gateway.comp.RequestProcessor;
import it.zwets.sms.gateway.comp.ResponseProducer;

@Component
public class SmsRouter extends RouteBuilder {
    
    private static final Logger LOG = LoggerFactory.getLogger(SmsRouter.class);
    
    public static String RESPOND = "direct:respond";

    @EndpointInject(Constants.ENDPOINT_FRONTEND_REQUEST)
    private Endpoint frontIn;
    
    @EndpointInject(Constants.ENDPOINT_FRONTEND_RESPONSE)
    private Endpoint frontOut;

    @Autowired
    private RequestProcessor requestProcessor;
    
    @Autowired
    private PayloadDecoder payloadDecoder;
    
    @Autowired
    private ResponseProducer responseProducer;
    
    @Override
    public void configure() throws Exception {
        
        // Global exception handler, respond with FAILED status
        // See: https://camel.apache.org/manual/exception-clause.html
        onException(Throwable.class).routeId("exception")
            .log(LoggingLevel.ERROR, LOG, "Exception ${exception}: ${exception.stacktrace}")
            .handled(true)
            .setHeader(HEADER_SMS_STATUS, constant(SMS_STATUS_FAILED))
            .setHeader(HEADER_ERROR_TEXT, simple("Exception while handling request: ${exception.message}"))
            .to(RESPOND);
        
        from(frontIn).routeId("main")
            .log(LoggingLevel.DEBUG, "Main route starting with request: ${body}")
            .process(requestProcessor)
            .process(payloadDecoder)
            .choice()
                .when(header(HEADER_SMS_STATUS).isNotNull())
                    .to(RESPOND)
                .when(header(HEADER_CLIENT_ID).isEqualTo("test"))
                    .to(TestClientRoute.TEST_ROUTE)
                .when(header(HEADER_CLIENT_ID).isEqualTo("live"))
                    .to(VodaWaspRoute.VODA_WASP_ROUTE)
                .otherwise()
                    .setHeader(HEADER_SMS_STATUS, constant(SMS_STATUS_INVALID))
                    .setHeader(HEADER_ERROR_TEXT, constant("Client not yet supported"))
                    .to(RESPOND);
                    
        from(RESPOND).routeId("response")
            .process(responseProducer)
            .filter(header(HEADER_CLIENT_ID).isNotNull())
            .filter(header(HEADER_CORREL_ID).isNotNull())
            .marshal().json()
            .to(frontOut);
        
    }
}
