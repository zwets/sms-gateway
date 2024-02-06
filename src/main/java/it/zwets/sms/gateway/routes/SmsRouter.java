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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import it.zwets.sms.gateway.SmsGatewayConfiguration.Constants;
import it.zwets.sms.gateway.comp.PayloadDecoder;
import it.zwets.sms.gateway.comp.RequestProcessor;
import it.zwets.sms.gateway.comp.ResponseProducer;
import it.zwets.sms.gateway.comp.VodaRequestProducer;
import it.zwets.sms.gateway.comp.VodaResponseProcessor;
import it.zwets.sms.gateway.dto.VodaResponse;

@Component
public class SmsRouter extends RouteBuilder {
    
//    private static final Logger LOG = LoggerFactory.getLogger(SmsRouter.class);    

    @EndpointInject(Constants.ENDPOINT_FRONTEND_REQUEST)
    private Endpoint frontIn;
    
    @EndpointInject(Constants.ENDPOINT_FRONTEND_RESPONSE)
    private Endpoint frontOut;

    @EndpointInject(Constants.ENDPOINT_BACKEND_REQUEST)
    private Endpoint backend;
    
    @Autowired
    private RequestProcessor requestProcessor;
    
    @Autowired
    private PayloadDecoder payloadDecoder;
    
    @Autowired
    private ResponseProducer responseProducer;
    
    @Autowired
    private VodaRequestProducer vodaRequestProducer;
    
    @Autowired
    private VodaResponseProcessor vodaResponseProcessor;
    
    @Override
    public void configure() throws Exception {
        
//        Predicate client = bodyAs(SendSmsRequest.class);
//        validator().type("incoming").withJava(IncomingValidator.class);

        // todo: make finer grained, set retry (if backend-related) etc
        // see: https://camel.apache.org/manual/exception-clause.html
        onException(Throwable.class).routeId("exception")
            .log("Exception ${exception}: ${exception.stacktrace}")
            .handled(true)
            .setHeader(HEADER_SMS_STATUS, constant(SMS_STATUS_FAILED))
            .setHeader(HEADER_ERROR_TEXT, simple("exception while handling request: ${exception.message}"))
            .to("direct:respond");
        
        from(frontIn).routeId("main")
            .log(LoggingLevel.DEBUG, "Main route starting with request: ${body}")
            .process(requestProcessor)
            .process(payloadDecoder)
            .choice()
                .when(header(HEADER_SMS_STATUS).isNotNull())
                    .to("direct:respond")
                .when(header(HEADER_CLIENT_ID).isEqualTo("test"))
                    .to("direct:test")
                .when(header(HEADER_CLIENT_ID).isEqualTo("live"))
                    .to("direct:live")
                .otherwise()
                    .setHeader(HEADER_SMS_STATUS, constant(SMS_STATUS_INVALID))
                    .setHeader(HEADER_ERROR_TEXT, constant("Client not yet supported"))
                    .to("direct:respond");
                    
        from("direct:respond").routeId("response")
            .process(responseProducer)
            .filter(header(HEADER_CLIENT_ID).isNotNull())
            .filter(header(HEADER_CORREL_ID).isNotNull())
            .marshal().json()
            .to(frontOut);
        
        from("direct:live").routeId("live")
            .log("LIVE: testing live send")
            .process(vodaRequestProducer)
            .choice()
                .when(header(HEADER_SMS_STATUS).isNotNull())
                    .to("direct:respond")
                .otherwise()
                    .marshal().jacksonXml()
                    .to(backend)
                    .log("BACKEND RESPONSE: ${body}")
                    .unmarshal().jacksonXml(VodaResponse.class)
                    .process(vodaResponseProcessor)
                    .to("direct:respond");
    }
}
