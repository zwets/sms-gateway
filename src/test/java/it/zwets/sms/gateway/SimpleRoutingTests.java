package it.zwets.sms.gateway;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.apache.camel.EndpointInject;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.spring.junit5.CamelSpringBootTest;
import org.apache.camel.test.spring.junit5.DisableJmx;
import org.apache.camel.test.spring.junit5.ExcludeRoutes;
import org.apache.camel.test.spring.junit5.MockEndpoints;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import it.zwets.sms.gateway.routes.SmsRouter;

// See: https://camel.apache.org/components/4.0.x/others/test-spring-junit5.html
@SpringBootTest(/* classes = configuration classes, properties = specific properties */) 
@CamelSpringBootTest
@EnableAutoConfiguration
@ExcludeRoutes(SmsRouter.class)
@MockEndpoints("direct:end")
@DisableJmx
class SimpleRoutingTests {

    @Produce("direct:start?block=false")
    private ProducerTemplate template;

    @EndpointInject("mock:direct:end?shadow=true")
    private MockEndpoint mock;

    @Configuration
    static class TestConfig {
        @Bean RouteBuilder testRoutes() {
            return new RouteBuilder() {
                @Override
                public void configure() throws Exception {
                    from ("direct:start").to("direct:end");
                }
            };
        }
    }
    
    @Test
    public void weHaveTemplate() {
        assertNotNull(template);
    }
    
    @Test
    public void weHaveTheMock() {
        assertNotNull(mock);
    }
    
    @Test
    public void testReceive() throws Exception {
        mock.expectedBodiesReceived("Hello");
        template.sendBody("Hello");
        mock.assertIsSatisfied();
    }
}
