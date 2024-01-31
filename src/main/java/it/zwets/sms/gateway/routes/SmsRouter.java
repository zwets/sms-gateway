package it.zwets.sms.gateway.routes;

import org.apache.camel.Endpoint;
import org.apache.camel.EndpointInject;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import it.zwets.sms.gateway.SmsGatewayConfiguration.Constants;
import it.zwets.sms.gateway.comp.RequestProcessor;
import it.zwets.sms.gateway.comp.ResponseProducer;

@Component
public class SmsRouter extends RouteBuilder {
    
//    private static final Logger LOG = LoggerFactory.getLogger(SmsRouter.class);    

    @EndpointInject(Constants.ENDPOINT_FRONTEND_REQUEST)
    private Endpoint frontIn;
    
    @EndpointInject(Constants.ENDPOINT_FRONTEND_RESPONSE)
    private Endpoint frontOut;

    @Autowired
    private RequestProcessor requestProcessor;
    
    @Autowired
    private ResponseProducer responseProducer;
    
//    @EndpointInject("backEndRequest")
//    private Endpoint backOut;
//    
//    @EndpointInject("backEndResponse")
//    private Endpoint backIn;
    
    @Override
    public void configure() throws Exception {
        
//        Predicate client = bodyAs(SendSmsRequest.class);
//        validator().type("incoming").withJava(IncomingValidator.class);

        // todo: make finer grained, set retry (if backend-related) etc
        // see: https://camel.apache.org/manual/exception-clause.html
        onException(Throwable.class).routeId("exception")
            .log("Exception occurred: ${exception.message}")
            .handled(true)
            .setProperty(Constants.OUT_FIELD_SMS_STATUS, constant(Constants.SMS_STATUS_FAILED))
            .setProperty(Constants.OUT_FIELD_ERROR_TEXT, constant("an exception occurred while handling request"))
            .to("direct:respond");
        
        from(frontIn).routeId("main")
            .log(LoggingLevel.DEBUG, "Main route starting with request: ${body}")
            .process(requestProcessor)
            .choice()
                .when(e -> e.getProperty(Constants.OUT_FIELD_SMS_STATUS) != null)
                    .to("direct:respond")
                .when(simple("${body.clientId} == 'test'"))
                    .to("direct:test")
                .otherwise()
                    .setProperty(Constants.OUT_FIELD_SMS_STATUS, constant(Constants.SMS_STATUS_INVALID))
                    .setProperty(Constants.OUT_FIELD_ERROR_TEXT, constant("Only client 'test' is supported for now."))
                    .to("direct:respond");
                    
        from("direct:respond").routeId("response")
            .process(responseProducer)
            .filter(e -> e.getProperty(Constants.OUT_FIELD_CLIENT_ID) != null && e.getProperty(Constants.OUT_FIELD_CORREL_ID) != null)
            .marshal().json()
            .to(frontOut);

        from("direct:delayed-respond").routeId("delay")
            .delay(1500)
            .to("direct:respond");
        
        from("direct:test").routeId("test")
            .choice()
                .when(simple("${body.payload.contains('S0D0')}"))
                    .log("SODO: not responding")
                .when(simple("${body.payload.contains('S0D1')}"))
                    .log("S0D1: responding DELIVERED only")
                    .setProperty(Constants.OUT_FIELD_SMS_STATUS, constant(Constants.SMS_STATUS_DELIVERED))
                    .to("direct:respond")
                .when(simple("${body.payload.contains('S1D0')}"))
                    .log("S1D0: responding SENT only")
                    .setProperty(Constants.OUT_FIELD_SMS_STATUS, constant(Constants.SMS_STATUS_SENT))
                    .to("direct:respond")
                .when(simple("${body.payload.contains('S1DX')}"))
                    .log("S1DX: responding SENT first")
                    .setProperty(Constants.OUT_FIELD_SMS_STATUS, constant(Constants.SMS_STATUS_SENT))
                    .to("direct:respond")
                    .log("S1DX: responding FAILED instead of DELIVERED")
                    .setProperty(Constants.OUT_FIELD_SMS_STATUS, constant(Constants.SMS_STATUS_FAILED))
                    .setProperty(Constants.OUT_FIELD_ERROR_TEXT, constant("failed after successful send"))
                    .to("direct:delayed-respond")
                .when(simple("${body.payload.contains('S1D1')}"))
                    .log("S1D1: responding SENT first")
                    .setProperty(Constants.OUT_FIELD_SMS_STATUS, constant(Constants.SMS_STATUS_SENT))
                    .to("direct:respond")
                    .log("S1D1: responding DELIVERED second")
                    .setProperty(Constants.OUT_FIELD_SMS_STATUS, constant(Constants.SMS_STATUS_DELIVERED))
                    .to("direct:delayed-respond")
                .when(simple("${body.payload.contains('S2D0')}"))
                    .log("S2D0: responding SENT first")
                    .setProperty(Constants.OUT_FIELD_SMS_STATUS, constant(Constants.SMS_STATUS_SENT))
                    .to("direct:respond")
                    .log("S2D0: responding SENT again")
                    .setProperty(Constants.OUT_FIELD_SMS_STATUS, constant(Constants.SMS_STATUS_SENT))
                    .to("direct:delayed-respond")
                .when(simple("${body.payload.contains('D1S1')}"))
                    .log("D1S1: responding DELIVERED first")
                    .setProperty(Constants.OUT_FIELD_SMS_STATUS, constant(Constants.SMS_STATUS_DELIVERED))
                    .to("direct:respond")
                    .log("D1S1: responding SENT after DELIVERED")
                    .setProperty(Constants.OUT_FIELD_SMS_STATUS, constant(Constants.SMS_STATUS_SENT))
                    .to("direct:delayed-respond")
                .when(simple("${body.payload.contains('DXS1')}"))
                    .log("DXS1: responding FAILED first")
                    .setProperty(Constants.OUT_FIELD_SMS_STATUS, constant(Constants.SMS_STATUS_FAILED))
                    .setProperty(Constants.OUT_FIELD_ERROR_TEXT, constant("reporting failed before reporting sent"))
                    .to("direct:respond")
                    .log("DXS1: responding SENT after FAILED")
                    .setProperty(Constants.OUT_FIELD_SMS_STATUS, constant(Constants.SMS_STATUS_SENT))
                    .to("direct:delayed-respond")
                .when(simple("${body.payload.contains('FAIL')}"))
                    .log("FAIL: responding FAILED")
                    .setProperty(Constants.OUT_FIELD_SMS_STATUS, constant(Constants.SMS_STATUS_FAILED))
                    .setProperty(Constants.OUT_FIELD_ERROR_TEXT, constant("you requested this to FAIL"))
                    .to("direct:delayed-respond")
                .otherwise()
                    .log("TEST: no marker found in incoming")
                    .setProperty(Constants.OUT_FIELD_SMS_STATUS, constant(Constants.SMS_STATUS_INVALID))
                    .setProperty(Constants.OUT_FIELD_ERROR_TEXT, constant("Test payload without S1D1 or other token"))
                    .to("direct:respond");
    }
}
