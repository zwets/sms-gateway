package it.zwets.sms.gateway.routes;

import static it.zwets.sms.gateway.SmsGatewayConfiguration.Constants.HEADER_ERROR_TEXT;
import static it.zwets.sms.gateway.SmsGatewayConfiguration.Constants.HEADER_SMS_STATUS;
import static it.zwets.sms.gateway.SmsGatewayConfiguration.Constants.SMS_STATUS_DELIVERED;
import static it.zwets.sms.gateway.SmsGatewayConfiguration.Constants.SMS_STATUS_FAILED;
import static it.zwets.sms.gateway.SmsGatewayConfiguration.Constants.SMS_STATUS_SENT;
import static org.apache.camel.LoggingLevel.INFO;

import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger LOG = LoggerFactory.getLogger(TestClientRoute.class);

    public static String TEST_ROUTE = "direct:test";
    public static String DELAY_RESPOND = "direct:delayed-respond";

    @Override
    public void configure() throws Exception {

        onException(Throwable.class).routeId("test-route-exception")
            .log(LoggingLevel.ERROR, LOG, "Exception in Test Route: ${exception}: ${exception.stacktrace}")
            .handled(true)
            .setHeader(HEADER_SMS_STATUS, constant(SMS_STATUS_FAILED))
            .setHeader(HEADER_ERROR_TEXT, simple("Exception while handling request: ${exception.message}"))
            .to(SmsRouter.RESPOND);

        from(DELAY_RESPOND).routeId("delay")
            .delay(1500)
            .to(SmsRouter.RESPOND);

        from(TEST_ROUTE).routeId("test")
            .setBody(bodyAs(SmsMessage.class).method("getBody()"))
            .choice()
                .when(body().contains("S0D0"))
                    .log(INFO, LOG, "SODO: not responding")
                .when(body().contains("S0D1"))
                    .log(INFO, LOG, "S0D1: responding DELIVERED only")
                    .setHeader(HEADER_SMS_STATUS, constant(SMS_STATUS_DELIVERED))
                    .to(SmsRouter.RESPOND)
                .when(body().contains("S1D0"))
                    .log(INFO, LOG, "S1D0: responding SENT only")
                    .setHeader(HEADER_SMS_STATUS, constant(SMS_STATUS_SENT))
                    .to(SmsRouter.RESPOND)
                .when(body().contains("S1DX"))
                    .log(INFO, LOG, "S1DX: responding SENT first")
                    .setHeader(HEADER_SMS_STATUS, constant(SMS_STATUS_SENT))
                    .to(SmsRouter.RESPOND)
                    .log(INFO, LOG, "S1DX: responding FAILED instead of DELIVERED")
                    .setHeader(HEADER_SMS_STATUS, constant(SMS_STATUS_FAILED))
                    .setHeader(HEADER_ERROR_TEXT, constant("Failed after successful send"))
                    .to(DELAY_RESPOND)
                .when(body().contains("S1D1"))
                    .log(INFO, LOG, "S1D1: responding SENT first")
                    .setHeader(HEADER_SMS_STATUS, constant(SMS_STATUS_SENT))
                    .to(SmsRouter.RESPOND)
                    .log(INFO, LOG, "S1D1: responding DELIVERED second")
                    .setHeader(HEADER_SMS_STATUS, constant(SMS_STATUS_DELIVERED))
                    .to(DELAY_RESPOND)
                .when(body().contains("S2D0"))
                    .log(INFO, LOG, "S2D0: responding SENT first")
                    .setHeader(HEADER_SMS_STATUS, constant(SMS_STATUS_SENT))
                    .to(SmsRouter.RESPOND)
                    .log(INFO, LOG, "S2D0: responding SENT again")
                    .setHeader(HEADER_SMS_STATUS, constant(SMS_STATUS_SENT))
                    .to(DELAY_RESPOND)
                .when(body().contains("D1S1"))
                    .log(INFO, LOG, "D1S1: responding DELIVERED first")
                    .setHeader(HEADER_SMS_STATUS, constant(SMS_STATUS_DELIVERED))
                    .to(SmsRouter.RESPOND)
                    .log(INFO, LOG, "D1S1: responding SENT after DELIVERED")
                    .setHeader(HEADER_SMS_STATUS, constant(SMS_STATUS_SENT))
                    .to(DELAY_RESPOND)
                .when(body().contains("DXS1"))
                    .log(INFO, LOG, "DXS1: responding FAILED first")
                    .setHeader(HEADER_SMS_STATUS, constant(SMS_STATUS_FAILED))
                    .setHeader(HEADER_ERROR_TEXT, constant("Reporting failed before reporting sent"))
                    .to(SmsRouter.RESPOND)
                    .log(INFO, LOG, "DXS1: responding SENT after FAILED")
                    .setHeader(HEADER_SMS_STATUS, constant(SMS_STATUS_SENT))
                    .to(DELAY_RESPOND)
                .when(body().contains("FAIL"))
                    .log(INFO, LOG, "FAIL: responding FAILED")
                    .setHeader(HEADER_SMS_STATUS, constant(SMS_STATUS_FAILED))
                    .setHeader(HEADER_ERROR_TEXT, constant("You requested this to FAIL"))
                    .to(DELAY_RESPOND)
                .otherwise()
                    .log(INFO, LOG, "test route: no marker: respond SENT first")
                    .setHeader(HEADER_SMS_STATUS, constant(SMS_STATUS_SENT))
                    .to(SmsRouter.RESPOND)
                    .log(INFO, LOG, "test route: no marker: responding DELIVERED second")
                    .setHeader(HEADER_SMS_STATUS, constant(SMS_STATUS_DELIVERED))
                    .to(DELAY_RESPOND);
    }
}
