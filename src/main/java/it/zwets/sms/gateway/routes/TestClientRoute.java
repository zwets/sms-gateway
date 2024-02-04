package it.zwets.sms.gateway.routes;

import static it.zwets.sms.gateway.SmsGatewayConfiguration.Constants.HEADER_ERROR_TEXT;
import static it.zwets.sms.gateway.SmsGatewayConfiguration.Constants.HEADER_SMS_STATUS;
import static it.zwets.sms.gateway.SmsGatewayConfiguration.Constants.SMS_STATUS_DELIVERED;
import static it.zwets.sms.gateway.SmsGatewayConfiguration.Constants.SMS_STATUS_FAILED;
import static it.zwets.sms.gateway.SmsGatewayConfiguration.Constants.SMS_STATUS_INVALID;
import static it.zwets.sms.gateway.SmsGatewayConfiguration.Constants.SMS_STATUS_SENT;

import org.apache.camel.builder.RouteBuilder;
import org.springframework.stereotype.Component;

import it.zwets.sms.gateway.dto.SmsMessage;

/**
 * Camel route for requests coming from client-id 'test'.
 * 
 * This route is like a mock: it triggers on specific markers in
 * the incoming message, to force a pattern of return messages.
 */
@Component
public class TestClientRoute extends RouteBuilder {
    
    public static String RESPOND = "direct:respond";
    public static String DELAY_RESPOND = "direct:delayed-respond";
    
    @Override
    public void configure() throws Exception {
        
        from(DELAY_RESPOND).routeId("delay")
            .delay(1500)
            .to(RESPOND);
        
        from("direct:test").routeId("test")
            .setBody(bodyAs(SmsMessage.class).method("getBody()"))
            .choice()
                .when(body().contains("S0D0"))
                    .log("SODO: not responding")
                .when(body().contains("S0D1"))
                    .log("S0D1: responding DELIVERED only")
                    .setHeader(HEADER_SMS_STATUS, constant(SMS_STATUS_DELIVERED))
                    .to(RESPOND)
                .when(body().contains("S1D0"))
                    .log("S1D0: responding SENT only")
                    .setHeader(HEADER_SMS_STATUS, constant(SMS_STATUS_SENT))
                    .to(RESPOND)
                .when(body().contains("S1DX"))
                    .log("S1DX: responding SENT first")
                    .setHeader(HEADER_SMS_STATUS, constant(SMS_STATUS_SENT))
                    .to(RESPOND)
                    .log("S1DX: responding FAILED instead of DELIVERED")
                    .setHeader(HEADER_SMS_STATUS, constant(SMS_STATUS_FAILED))
                    .setHeader(HEADER_ERROR_TEXT, constant("failed after successful send"))
                    .to(DELAY_RESPOND)
                .when(body().contains("S1D1"))
                    .log("S1D1: responding SENT first")
                    .setHeader(HEADER_SMS_STATUS, constant(SMS_STATUS_SENT))
                    .to(RESPOND)
                    .log("S1D1: responding DELIVERED second")
                    .setHeader(HEADER_SMS_STATUS, constant(SMS_STATUS_DELIVERED))
                    .to(DELAY_RESPOND)
                .when(body().contains("S2D0"))
                    .log("S2D0: responding SENT first")
                    .setHeader(HEADER_SMS_STATUS, constant(SMS_STATUS_SENT))
                    .to(RESPOND)
                    .log("S2D0: responding SENT again")
                    .setHeader(HEADER_SMS_STATUS, constant(SMS_STATUS_SENT))
                    .to(DELAY_RESPOND)
                .when(body().contains("D1S1"))
                    .log("D1S1: responding DELIVERED first")
                    .setHeader(HEADER_SMS_STATUS, constant(SMS_STATUS_DELIVERED))
                    .to(RESPOND)
                    .log("D1S1: responding SENT after DELIVERED")
                    .setHeader(HEADER_SMS_STATUS, constant(SMS_STATUS_SENT))
                    .to(DELAY_RESPOND)
                .when(body().contains("DXS1"))
                    .log("DXS1: responding FAILED first")
                    .setHeader(HEADER_SMS_STATUS, constant(SMS_STATUS_FAILED))
                    .setHeader(HEADER_ERROR_TEXT, constant("reporting failed before reporting sent"))
                    .to(RESPOND)
                    .log("DXS1: responding SENT after FAILED")
                    .setHeader(HEADER_SMS_STATUS, constant(SMS_STATUS_SENT))
                    .to(DELAY_RESPOND)
                .when(body().contains("FAIL"))
                    .log("FAIL: responding FAILED")
                    .setHeader(HEADER_SMS_STATUS, constant(SMS_STATUS_FAILED))
                    .setHeader(HEADER_ERROR_TEXT, constant("you requested this to FAIL"))
                    .to(DELAY_RESPOND)
                .otherwise()
                    .log("TEST: no marker found in incoming")
                    .setHeader(HEADER_SMS_STATUS, constant(SMS_STATUS_INVALID))
                    .setHeader(HEADER_ERROR_TEXT, constant("Test body without S1D1 or other token"))
                    .to(RESPOND);
    }
}
