package it.zwets.sms.gateway;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.time.Instant;

import org.apache.camel.CamelContext;
import org.apache.camel.EndpointInject;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.spring.junit5.CamelSpringBootTest;
import org.apache.camel.test.spring.junit5.DisableJmx;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;

import it.zwets.sms.gateway.SmsGatewayConfiguration.Constants;
import it.zwets.sms.gateway.comp.RequestProcessor;
import it.zwets.sms.gateway.comp.ResponseProducer;
import it.zwets.sms.gateway.dto.SendSmsRequest;
import it.zwets.sms.gateway.routes.SmsRouter;

@SpringBootTest(classes = {MockConfiguration.class, SmsRouter.class, RequestProcessor.class, ResponseProducer.class} /* properties = specific properties */)
@CamelSpringBootTest
@EnableAutoConfiguration
@DisableJmx
//@ExcludeRoutes(SmsRouter.class)
//@MockEndpoints(Constants.ENDPOINT_FRONTEND_RESPONSE) // not needed we override the whole bean (to not be Kafka) in the MockConfiguration
public class SmsGatewayServiceTest {
    
    private static String CORREL_ID = "my-correl-id";
    private static String CLIENT_ID = "test";
    private static String MESSAGE = "DummyMessage";
    
    @Autowired
    CamelContext context;
    
    @Produce("marshallingFrontEndRequestEndpoint") // defined in MockConfiguration
    private ProducerTemplate template;

    @EndpointInject("mockFrontEndResponseEndpoint") // defined in MockConfiguration
    private MockEndpoint response;
    
    @AfterEach
    private void afterEach() {
        response.reset();
    }
        // Check inner workings
   
    @Test
    public void shouldHaveCamelContext() {
        assertNotNull(context);
    }

    @Test
    public void shouldHaveRequestTemplate() {
        assertNotNull(template);
    }

    @Test
    public void shouldHaveResponseEndpoint() {
        assertNotNull(response);
    }
    
    @Test
    public void shouldHaveRoutes() {
        assertNotNull(context.getRoute("main"));
    }
    
        // General (non client-dependent) tests
    
    @Test
    public void noResponseOnNonJson() throws InterruptedException {

        response.setAssertPeriod(100);
        response.expectedMessageCount(0);
        
        template.sendBody("this-is-not-json");
        
        response.assertIsSatisfied();
    }
    
    @Test
    public void noResponseOnEmptyJson() throws InterruptedException {

        response.setAssertPeriod(100);
        response.expectedMessageCount(0);
        
        template.sendBody(new SendSmsRequest(null, null, null, null));
        
        response.assertIsSatisfied();
    }
    
    @Test
    public void noResponseOnMissingClientId() throws InterruptedException {

        response.setAssertPeriod(100);
        response.expectedMessageCount(0);
        
        template.sendBody(new SendSmsRequest(null, CORREL_ID, makeDeadline(100), MESSAGE));
        
        response.assertIsSatisfied();
    }
    
    @Test
    public void noResponseOnMissingCorrelId() throws InterruptedException {

        response.setAssertPeriod(100);
        response.expectedMessageCount(0);
        
        template.sendBody(new SendSmsRequest(CLIENT_ID, null, makeDeadline(100), MESSAGE));
        
        response.assertIsSatisfied();
    }
    
    @Test
    public void errorOnMissingDeadline() throws InterruptedException {

        response.expectedMessageCount(1);
        response.message(0).jsonpath("$['correl-id']").isEqualTo(CORREL_ID);
        response.message(0).jsonpath("$['client-id']").isEqualTo(CLIENT_ID);
        response.message(0).jsonpath("$['sms-status']").isEqualTo(Constants.SMS_STATUS_INVALID);
        response.message(0).jsonpath("$['error-text']").isNotNull();
        
        template.sendBody(new SendSmsRequest(CLIENT_ID, CORREL_ID, null, MESSAGE));
        
        response.assertIsSatisfied();
    }
    
    @Test
    public void errorOnMissingMessage() throws InterruptedException {

        response.expectedMessageCount(1);
        response.message(0).jsonpath("$['correl-id']").isEqualTo(CORREL_ID);
        response.message(0).jsonpath("$['client-id']").isEqualTo(CLIENT_ID);
        response.message(0).jsonpath("$['sms-status']").isEqualTo(Constants.SMS_STATUS_INVALID);
        response.message(0).jsonpath("$['error-text']").isNotNull();
        
        template.sendBody(new SendSmsRequest(CLIENT_ID, CORREL_ID, makeDeadline(100), null));
        
        response.assertIsSatisfied();
    }

    @Test
    public void expiredOnArrival() throws InterruptedException {
        
        response.expectedMessageCount(1);
        response.message(0).jsonpath("$['correl-id']").isEqualTo(CORREL_ID);
        response.message(0).jsonpath("$['client-id']").isEqualTo(CLIENT_ID);
        response.message(0).jsonpath("$['sms-status']").isEqualTo(Constants.SMS_STATUS_EXPIRED);
        response.message(0).jsonpath("$..['error-text'].length()").isEqualTo(0);
        
        template.sendBody(makeSmsRequest(Instant.now().minusMillis(100), "no content"));
        
        response.assertIsSatisfied();
    }
    
    @Test
    public void invalidDeadline() throws InterruptedException {
        response.expectedMessageCount(1);
        response.message(0).jsonpath("$['sms-status']").isEqualTo(Constants.SMS_STATUS_INVALID);
        
        template.sendBody(makeSmsRequest("this-is-not-iso-8601", "S1D1"));
        
        response.assertIsSatisfied();        
    }

    @Test
    public void happyNormalFlow() throws InterruptedException {
        
        response.expectedMessageCount(2);
        
        response.message(0).jsonpath("$['correl-id']").isEqualTo(CORREL_ID);
        response.message(0).jsonpath("$['client-id']").isEqualTo(CLIENT_ID);
        response.message(0).jsonpath("$['sms-status']").isEqualTo(Constants.SMS_STATUS_SENT);
        response.message(0).jsonpath("$..['error-text'].length()").isEqualTo(0);
        response.message(1).jsonpath("$['correl-id']").isEqualTo(CORREL_ID);
        response.message(1).jsonpath("$['client-id']").isEqualTo(CLIENT_ID);
        response.message(1).jsonpath("$['sms-status']").isEqualTo(Constants.SMS_STATUS_DELIVERED);
        response.message(1).jsonpath("$..['error-text'].length()").isEqualTo(0);

        template.sendBody(makeSmsRequest("S1D1"));
        
        response.assertIsSatisfied();
    }
    
    @Test
    public void noResponseS0D0() throws InterruptedException {
        
        response.setAssertPeriod(100);
        response.expectedMessageCount(0);

        template.sendBody(makeSmsRequest("S0D0"));
        
        response.assertIsSatisfied();
    }
    
    @Test
    public void oneResponseS0D1() throws InterruptedException {
        
        response.setAssertPeriod(100);
        response.expectedMessageCount(1);
        response.message(0).jsonpath("$['sms-status']").isEqualTo(Constants.SMS_STATUS_DELIVERED);

        template.sendBody(makeSmsRequest("S0D1"));
        
        response.assertIsSatisfied();
    }
    
    @Test
    public void oneResponseS1D0() throws InterruptedException {
        
        response.setAssertPeriod(100);
        response.expectedMessageCount(1);
        response.message(0).jsonpath("$['correl-id']").isEqualTo(CORREL_ID);
        response.message(0).jsonpath("$['client-id']").isEqualTo(CLIENT_ID);
        response.message(0).jsonpath("$['sms-status']").isEqualTo(Constants.SMS_STATUS_SENT);
        response.message(0).jsonpath("$..['error-text'].length()").isEqualTo(0);        

        template.sendBody(makeSmsRequest("S1D0"));
        
        response.assertIsSatisfied();
    }
    
    @Test
    public void twoResponseS1DX() throws InterruptedException {
        
        response.expectedMessageCount(2);
        
        response.message(0).jsonpath("$['correl-id']").isEqualTo(CORREL_ID);
        response.message(0).jsonpath("$['client-id']").isEqualTo(CLIENT_ID);
        response.message(0).jsonpath("$['sms-status']").isEqualTo(Constants.SMS_STATUS_SENT);
        response.message(0).jsonpath("$..['error-text'].length()").isEqualTo(0);        
        response.message(1).jsonpath("$['correl-id']").isEqualTo(CORREL_ID);
        response.message(1).jsonpath("$['client-id']").isEqualTo(CLIENT_ID);
        response.message(1).jsonpath("$['sms-status']").isEqualTo(Constants.SMS_STATUS_FAILED);
        response.message(1).jsonpath("$['error-text']").isNotNull();

        template.sendBody(makeSmsRequest("S1DX"));
        
        response.assertIsSatisfied();
    }
    
    @Test
    public void twoResponseS2D0() throws InterruptedException {
        
        response.expectedMessageCount(2);
        
        response.message(0).jsonpath("$['correl-id']").isEqualTo(CORREL_ID);
        response.message(0).jsonpath("$['client-id']").isEqualTo(CLIENT_ID);
        response.message(0).jsonpath("$['sms-status']").isEqualTo(Constants.SMS_STATUS_SENT);
        response.message(0).jsonpath("$..['error-text'].length()").isEqualTo(0);        
        response.message(1).jsonpath("$['correl-id']").isEqualTo(CORREL_ID);
        response.message(1).jsonpath("$['client-id']").isEqualTo(CLIENT_ID);
        response.message(1).jsonpath("$['sms-status']").isEqualTo(Constants.SMS_STATUS_SENT);
        response.message(1).jsonpath("$..['error-text'].length()").isEqualTo(0);        

        template.sendBody(makeSmsRequest("S2D0"));
        
        response.assertIsSatisfied();
    }

    @Test
    public void inverseOrderD1S1() throws InterruptedException {
        
        response.expectedMessageCount(2);
        
        response.message(0).jsonpath("$['correl-id']").isEqualTo(CORREL_ID);
        response.message(0).jsonpath("$['client-id']").isEqualTo(CLIENT_ID);
        response.message(0).jsonpath("$['sms-status']").isEqualTo(Constants.SMS_STATUS_DELIVERED);
        response.message(0).jsonpath("$..['error-text'].length()").isEqualTo(0);
        response.message(1).jsonpath("$['correl-id']").isEqualTo(CORREL_ID);
        response.message(1).jsonpath("$['client-id']").isEqualTo(CLIENT_ID);
        response.message(1).jsonpath("$['sms-status']").isEqualTo(Constants.SMS_STATUS_SENT);
        response.message(1).jsonpath("$..['error-text'].length()").isEqualTo(0);
    
        template.sendBody(makeSmsRequest("D1S1"));
    
        response.assertIsSatisfied();
    }

    @Test
    public void failThenSentDXS1() throws InterruptedException {
        
        response.expectedMessageCount(2);
        
        response.message(0).jsonpath("$['correl-id']").isEqualTo(CORREL_ID);
        response.message(0).jsonpath("$['client-id']").isEqualTo(CLIENT_ID);
        response.message(0).jsonpath("$['sms-status']").isEqualTo(Constants.SMS_STATUS_FAILED);
        response.message(0).jsonpath("$['error-text']").isNotNull();
        response.message(1).jsonpath("$['correl-id']").isEqualTo(CORREL_ID);
        response.message(1).jsonpath("$['client-id']").isEqualTo(CLIENT_ID);
        response.message(1).jsonpath("$['sms-status']").isEqualTo(Constants.SMS_STATUS_SENT);
        response.message(1).jsonpath("$..['error-text'].length()").isEqualTo(0);        

        template.sendBody(makeSmsRequest("DXS1"));
        
        response.assertIsSatisfied();
    }
    
    @Test
    public void oneResponseFAIL() throws InterruptedException {
        
        response.setAssertPeriod(100);
        response.expectedMessageCount(1);
        response.message(0).jsonpath("$['correl-id']").isEqualTo(CORREL_ID);
        response.message(0).jsonpath("$['client-id']").isEqualTo(CLIENT_ID);
        response.message(0).jsonpath("$['sms-status']").isEqualTo(Constants.SMS_STATUS_FAILED);
        response.message(0).jsonpath("$['error-text']").isNotNull();        

        template.sendBody(makeSmsRequest("FAIL"));
        
        response.assertIsSatisfied();
    }

    @Test
    public void invalidOnNoMarker() throws InterruptedException {

        response.expectedMessageCount(1);
        response.message(0).jsonpath("$['correl-id']").isEqualTo(CORREL_ID);
        response.message(0).jsonpath("$['client-id']").isEqualTo(CLIENT_ID);
        response.message(0).jsonpath("$['sms-status']").isEqualTo(Constants.SMS_STATUS_INVALID);
        response.message(0).jsonpath("$['error-text']").isNotNull();
        
        template.sendBody(makeSmsRequest("Nothing special in the message"));
        
        response.assertIsSatisfied();
    }
    
    private SendSmsRequest makeSmsRequest(String deadline, String message) {
        return new SendSmsRequest(CLIENT_ID, CORREL_ID, deadline, message);
    }

    private SendSmsRequest makeSmsRequest(Instant deadline, String message) {
        return makeSmsRequest(deadline.toString(), message);
    }

    private SendSmsRequest makeSmsRequest(String message) {
        return makeSmsRequest(makeDeadline(100), message);
    }
    
    private String makeDeadline(int millis) {
        return Instant.now().plusMillis(millis).toString();
    }
}
