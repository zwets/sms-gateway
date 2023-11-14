package it.zwets.sms.gateway.routes;

import java.time.Instant;

import org.apache.camel.BeanInject;
import org.apache.camel.Endpoint;
import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.Predicate;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import it.zwets.sms.gateway.SmsGatewayConfiguration.Constants;
import it.zwets.sms.gateway.dto.SendSmsRequest;

@Component
public class SmsRouter extends RouteBuilder {
    
    private static final Logger LOG = LoggerFactory.getLogger(SmsRouter.class);    

    @EndpointInject(Constants.ENDPOINT_FRONTEND_REQUEST)
    private Endpoint frontIn;
    
    @EndpointInject(Constants.ENDPOINT_FRONTEND_RESPONSE)
    private Endpoint frontOut;

    @BeanInject(Constants.BEAN_RESPONSE_MAKER)
    private Processor responseMaker;
    
//    @EndpointInject("backEndRequest")
//    private Endpoint backOut;
//    
//    @EndpointInject("backEndResponse")
//    private Endpoint backIn;
    
    private Predicate isExpired = new Predicate() {
        @Override public boolean matches(Exchange exchange) {
            try {
                return Instant.parse(exchange.getIn().getBody(SendSmsRequest.class).deadline()).isBefore(Instant.now());
            }
            catch(Exception e) {
                LOG.error("Invalid deadline value (returning EXPIRED): {}", exchange.getIn().getBody(SendSmsRequest.class).deadline());
                return true;
            }
        }
    };

    @Override
    public void configure() throws Exception {
        
//        Predicate client = bodyAs(SendSmsRequest.class);
//        validator().type("incoming").withJava(IncomingValidator.class);
        
        onException(Throwable.class).routeId("exception")
            .log("Exception occurred")
            .handled(true)
            .choice()
                .when(e -> e.getProperty(Constants.OUT_FIELD_CORREL_ID) != null)
                    .setProperty(Constants.OUT_FIELD_SMS_STATUS, constant(Constants.SMS_STATUS_FAILED))
                    .setProperty(Constants.OUT_FIELD_ERROR_CODE, constant(-1))
                    .setProperty(Constants.OUT_FIELD_ERROR_TEXT, constant("an exception occurred while handling request"))
                    .to("direct:respond");
        
        from(frontIn).routeId("main")
            .log("REQUEST: ${body}")
            .unmarshal().json(SendSmsRequest.class)
            .setProperty(Constants.OUT_FIELD_CORREL_ID, simple("${body.correlId}"))
            .setProperty(Constants.OUT_FIELD_CLIENT_ID, simple("${body.clientId}"))
            .setProperty(Constants.OUT_FIELD_ERROR_CODE, constant(0))
            .choice()
                .when(simple("${body.clientId} == null"))
                    .log(LoggingLevel.ERROR, "Request lacks client ID, dropping")
                .when(simple("${body.correlId} == null"))
                    .log(LoggingLevel.ERROR, "Request lacks correlation ID, dropping")
                .when(isExpired)
                    .setProperty(Constants.OUT_FIELD_SMS_STATUS, constant(Constants.SMS_STATUS_EXPIRED))
                    .to("direct:respond")
                .when(simple("${body.clientId} == 'test'"))
                    .to("direct:test")
                .otherwise()
                    .setProperty(Constants.OUT_FIELD_SMS_STATUS, constant(Constants.SMS_STATUS_INVALID))
                    .setProperty(Constants.OUT_FIELD_ERROR_CODE, constant(-1))
                    .setProperty(Constants.OUT_FIELD_ERROR_TEXT, constant("Only client 'test' is supported for now."))
                    .to("direct:respond");
                    
        from("direct:respond").routeId("response")
            .process(responseMaker)
            .marshal().json()
            .log("RESPONSE: ${body}")
            .to(frontOut);

        from("direct:delayed-respond").routeId("delay")
            .delay(1500)
            .to("direct:respond");
        
        from("direct:test").routeId("test")
            .choice()
                .when(simple("${body.message.contains('S0D0')}"))
                    .log("SODO: not responding")
                .when(simple("${body.message.contains('S0D1')}"))
                    .log("S0D1: responding DELIVERED only")
                    .setProperty(Constants.OUT_FIELD_SMS_STATUS, constant(Constants.SMS_STATUS_DELIVERED))
                    .to("direct:respond")
                .when(simple("${body.message.contains('S1D0')}"))
                    .log("S1D0: responding SENT only")
                    .setProperty(Constants.OUT_FIELD_SMS_STATUS, constant(Constants.SMS_STATUS_SENT))
                    .to("direct:respond")
                .when(simple("${body.message.contains('S1DX')}"))
                    .log("S1DX: responding SENT first")
                    .setProperty(Constants.OUT_FIELD_SMS_STATUS, constant(Constants.SMS_STATUS_SENT))
                    .to("direct:respond")
                    .log("S1DX: responding FAILED instead of DELIVERED")
                    .setProperty(Constants.OUT_FIELD_SMS_STATUS, constant(Constants.SMS_STATUS_FAILED))
                    .to("direct:delayed-respond")
                .when(simple("${body.message.contains('S1D1')}"))
                    .log("S1D1: responding SENT first")
                    .setProperty(Constants.OUT_FIELD_SMS_STATUS, constant(Constants.SMS_STATUS_SENT))
                    .to("direct:respond")
                    .log("S1D1: responding DELIVERED second")
                    .setProperty(Constants.OUT_FIELD_SMS_STATUS, constant(Constants.SMS_STATUS_DELIVERED))
                    .to("direct:delayed-respond")
                .when(simple("${body.message.contains('S2D0')}"))
                    .log("S2D0: responding SENT first")
                    .setProperty(Constants.OUT_FIELD_SMS_STATUS, constant(Constants.SMS_STATUS_SENT))
                    .to("direct:respond")
                    .log("S2D0: responding SENT again")
                    .setProperty(Constants.OUT_FIELD_SMS_STATUS, constant(Constants.SMS_STATUS_SENT))
                    .to("direct:delayed-respond")
                .when(simple("${body.message.contains('D1S1')}"))
                    .log("D1S1: responding DELIVERED first")
                    .setProperty(Constants.OUT_FIELD_SMS_STATUS, constant(Constants.SMS_STATUS_DELIVERED))
                    .to("direct:respond")
                    .log("D1S1: responding SENT after DELIVERED")
                    .setProperty(Constants.OUT_FIELD_SMS_STATUS, constant(Constants.SMS_STATUS_SENT))
                    .to("direct:delayed-respond")
                .when(simple("${body.message.contains('DXS1')}"))
                    .log("DXS1: responding FAILED first")
                    .setProperty(Constants.OUT_FIELD_SMS_STATUS, constant(Constants.SMS_STATUS_FAILED))
                    .to("direct:respond")
                    .log("DXS1: responding SENT after FAILED")
                    .setProperty(Constants.OUT_FIELD_SMS_STATUS, constant(Constants.SMS_STATUS_SENT))
                    .to("direct:delayed-respond")
                .when(simple("${body.message.contains('FAIL')}"))
                    .log("FAIL: responding FAILED")
                    .setProperty(Constants.OUT_FIELD_SMS_STATUS, constant(Constants.SMS_STATUS_FAILED))
                    .to("direct:delayed-respond")
                .otherwise()
                    .log("TEST: no marker found in incoming")
                    .setProperty(Constants.OUT_FIELD_SMS_STATUS, constant(Constants.SMS_STATUS_INVALID))
                    .setProperty(Constants.OUT_FIELD_ERROR_CODE, constant(-1))
                    .setProperty(Constants.OUT_FIELD_ERROR_TEXT, constant("Test message without S1D1 or other token"))
                    .to("direct:respond");
    }
}
