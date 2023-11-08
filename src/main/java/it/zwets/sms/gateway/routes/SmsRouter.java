package it.zwets.sms.gateway.routes;

import java.time.Instant;

import org.apache.camel.Endpoint;
import org.apache.camel.EndpointInject;
import org.apache.camel.Exchange;
import org.apache.camel.Predicate;
import org.apache.camel.builder.RouteBuilder;
import org.springframework.stereotype.Component;

import it.zwets.sms.gateway.SmsGatewayConfiguration.Constants;
import it.zwets.sms.gateway.dto.SendSmsRequest;

@Component
public class SmsRouter extends RouteBuilder {

    @EndpointInject("{{sms.gateway.kafka.inbound-uri}}")
    private Endpoint kafkaIn;
    
    @EndpointInject("{{sms.gateway.kafka.outbound-uri}}")
    private Endpoint kafkaOut;
    
    private Predicate isExpired = new Predicate() {
        @Override public boolean matches(Exchange exchange) {
            return Instant.parse(exchange.getIn().getBody(SendSmsRequest.class).deadline()).isBefore(Instant.now());
        }
    };

    @Override
    public void configure() throws Exception {
        
//        Predicate client = bodyAs(SendSmsRequest.class);
//        validator().type("incoming").withJava(IncomingValidator.class);
              
        from(kafkaIn)
            .log("${body}")
            .unmarshal().json(SendSmsRequest.class)
            .setProperty(Constants.OUT_FIELD_CORREL_ID, simple("${body.correlId}"))
            .setProperty(Constants.OUT_FIELD_CLIENT_ID, simple("${body.clientId}"))
            .choice()
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
                    
        from("direct:respond")
            .process("responseMaker")
            .marshal().json()
            .to(kafkaOut);

        from("direct:delayed-respond")
            .delay(1500)
            .to("direct:respond");
        
        from("direct:test")
            .choice()
                .when(simple("${body.message.contains('S0D0')}"))
                    .log("SODO: not sending anything")
                .when(simple("${body.message.contains('S1D0')}"))
                    .setProperty(Constants.OUT_FIELD_SMS_STATUS, constant(Constants.SMS_STATUS_SENT))
                    .to("direct:respond")
                .when(simple("${body.message.contains('S0D1')}"))
                    .setProperty(Constants.OUT_FIELD_SMS_STATUS, constant(Constants.SMS_STATUS_DELIVERED))
                    .to("direct:respond")
                .when(simple("${body.message.contains('S1D1')}"))
                    .setProperty(Constants.OUT_FIELD_SMS_STATUS, constant(Constants.SMS_STATUS_SENT))
                    .to("direct:respond")
                    .setProperty(Constants.OUT_FIELD_SMS_STATUS, constant(Constants.SMS_STATUS_DELIVERED))
                    .to("direct:delayed-respond")
                .when(simple("${body.message.contains('S2D1')}"))
                    .setProperty(Constants.OUT_FIELD_SMS_STATUS, constant(Constants.SMS_STATUS_SENT))
                    .to("direct:respond")
                    .setProperty(Constants.OUT_FIELD_SMS_STATUS, constant(Constants.SMS_STATUS_SENT))
                    .to("direct:delayed-respond")
                .when(simple("${body.message.contains('D1S1')}"))
                    .setProperty(Constants.OUT_FIELD_SMS_STATUS, constant(Constants.SMS_STATUS_DELIVERED))
                    .to("direct:respond")
                    .setProperty(Constants.OUT_FIELD_SMS_STATUS, constant(Constants.SMS_STATUS_SENT))
                    .to("direct:delayed-respond")
                .when(simple("${body.message.contains('FAIL')}"))
                    .setProperty(Constants.OUT_FIELD_SMS_STATUS, constant(Constants.SMS_STATUS_FAILED))
                    .to("direct:delayed-respond")
                .otherwise()
                    .log("TEST: no marker found in incoming");
    }
}
