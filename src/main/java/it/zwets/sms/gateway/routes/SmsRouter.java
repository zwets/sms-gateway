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
                    .bean("responseMaker", "expired")
                    .to(kafkaOut)
                .when(simple("${body.clientId} == 'test'"))
                    .to("direct:test")
                .otherwise()
                    .bean("responseMaker", "invalid(*, -1, 'Unknown UKNOWN')")
                    .to(kafkaOut);
                    
        from("direct:test")
            .log("TEST");
    }
}
