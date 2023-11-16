package it.zwets.sms.gateway;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import it.zwets.sms.gateway.SmsGatewayConfiguration.Constants;

@Configuration
public class MockConfiguration {
    
    /**
     * Endpoint that takes objects and marshals them to the actual request endpoint,
     * by dropping them on the route defined below.
     * @param camelContext
     * @return regular endpoint for dropping SmsRequest objects on
     */
    @Bean("marshallingFrontEndRequestEndpoint")
    public Endpoint getMarshallingFrontEndRequestEndpoint(CamelContext camelContext) {
        return camelContext.getEndpoint("direct:marshall-request");
    }
    
    /**
     * The Camel Mock over the frontEndResponse endpoint
     * @param camelContext
     * @return
     */
    @Bean("mockFrontEndResponseEndpoint")
    public MockEndpoint getMockFrontEndResponseEndpoint(CamelContext camelContext) {
        return (MockEndpoint) camelContext.getEndpoint("mock:direct:mock-kafka-out");
    }
    
    /**
     * Mocks the frontEndRequest endpoint that the main route listens on.
     * @param camelContext
     * @return direct endpoint into the main route
     */
    @Bean(Constants.ENDPOINT_FRONTEND_REQUEST)
    public Endpoint getFrontEndRequestEndpoint(CamelContext camelContext) {
        return camelContext.getEndpoint("direct:mock-kafka-in");
    }
    
    /**
     * Mocks the frontEndResponse endpoint that the main route listens on.
     * @param camelContext
     * @return direct endpoint the main route writes to
     */
    @Bean(Constants.ENDPOINT_FRONTEND_RESPONSE)
    public Endpoint getFrontEndResponseEndpoint(CamelContext camelContext) {
        return camelContext.getEndpoint("direct:mock-kafka-out");
    }

    /**
     * Convenience route that prepends to the mocked incoming endpoint,
     * and performs marshalling to JSON, so we can send SmsRequest objects
     * to 'direct:mock-request' which will then go as JSON string to the
     * actual mock into the router.
     */
    @Bean
    public RouteBuilder marshalToJsonRoute() {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:marshall-request").routeId("marshall-request")
                    .marshal().json()
                    .to(Constants.ENDPOINT_FRONTEND_REQUEST);
                from(Constants.ENDPOINT_FRONTEND_RESPONSE).routeId("mock-out")
                    .to("mockFrontEndResponseEndpoint");
            }
        };
    }
}
