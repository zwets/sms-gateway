package it.zwets.sms.gateway;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.time.Instant;

import org.apache.camel.CamelContext;
import org.apache.camel.EndpointInject;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.spring.junit5.CamelSpringBootTest;
import org.apache.camel.test.spring.junit5.MockEndpoints;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;

import it.zwets.sms.gateway.SmsGatewayConfiguration.Constants;
import it.zwets.sms.gateway.dto.SendSmsRequest;
import it.zwets.sms.gateway.routes.SmsRouter;

@SpringBootTest(classes = {MockConfiguration.class, SmsRouter.class})
@CamelSpringBootTest
@EnableAutoConfiguration
@MockEndpoints(Constants.ENDPOINT_FRONTEND_RESPONSE) // not needed we override the whole bean (to not be Kafka) in the MockConfiguration
class SmsGatewayServiceTests {

    
    private static String CORREL_ID = "my-correl-id";
    private static String CLIENT_ID = "test";
    
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
    public void noResponseOnNoJson() throws InterruptedException {

        response.setAssertPeriod(100);
        response.expectedMessageCount(0);
        
        template.sendBody("this-is-not-json");
        
        response.assertIsSatisfied();
    }
    
    @Test
    public void whatResponseOnNoJson() throws InterruptedException {

        response.setAssertPeriod(100);
        response.expectedMessageCount(0);
        
        template.sendBody("{ }");
        
        response.assertIsSatisfied();
    }
    

    @Test
    public void expiredOnArrival() throws InterruptedException {
        
        response.expectedMessageCount(1);
        response.message(0).jsonpath("$['correl-id']").isEqualTo(CORREL_ID);
        response.message(0).jsonpath("$['client-id']").isEqualTo(CLIENT_ID);
        response.message(0).jsonpath("$['sms-status']").isEqualTo(Constants.SMS_STATUS_EXPIRED);
        response.message(0).jsonpath("$['error-code']").isEqualTo(0);
        response.message(0).jsonpath("$..['error-text'].length()").isEqualTo(0);
        
        template.sendBody(makeSmsRequest(Instant.now().minusMillis(100), "no content"));
        
        response.assertIsSatisfied();
    }
    
    @Test
    public void invalidDeadlineOsExpired() throws InterruptedException {
        response.expectedMessageCount(1);
        response.message(0).jsonpath("$['sms-status']").isEqualTo(Constants.SMS_STATUS_EXPIRED);
        
        template.sendBody(makeSmsRequest("this-is-not-iso-8601", "S1D1"));
        
        response.assertIsSatisfied();        
    }

    @Test
    public void happyNornalFlow() throws InterruptedException {
        
        response.expectedMessageCount(2);
        
        response.message(0).jsonpath("$['sms-status']").isEqualTo(Constants.SMS_STATUS_SENT);
        response.message(1).jsonpath("$['sms-status']").isEqualTo(Constants.SMS_STATUS_DELIVERED);

        template.sendBody(makeSmsRequest("S1D1"));
        
        response.assertIsSatisfied();
    }
    

    private SendSmsRequest makeSmsRequest(String deadline, String message) {
        return new SendSmsRequest(CORREL_ID, CLIENT_ID, deadline, message);
    }

    private SendSmsRequest makeSmsRequest(Instant deadline, String message) {
        return makeSmsRequest(deadline.toString(), message);
    }

    private SendSmsRequest makeSmsRequest(String message) {
        return makeSmsRequest(Instant.now().plusMillis(100), message);
    }
}
