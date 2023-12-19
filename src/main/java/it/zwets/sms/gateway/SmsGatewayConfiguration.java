package it.zwets.sms.gateway;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.builder.endpoint.StaticEndpointBuilders;
import org.apache.camel.builder.endpoint.dsl.KafkaEndpointBuilderFactory.KafkaEndpointConsumerBuilder;
import org.apache.camel.builder.endpoint.dsl.KafkaEndpointBuilderFactory.KafkaEndpointProducerBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Application configuration.
 * 
 * Produces the beans needed by the application (if any).
 */
@Configuration(proxyBeanMethods = false)
public class SmsGatewayConfiguration {
    
    private static final Logger LOG = LoggerFactory.getLogger(SmsGatewayConfiguration.class);    

    private final CamelContext camelContext;
    
    private final KafkaEndpointConsumerBuilder kafkaInBuilder;
    private final KafkaEndpointProducerBuilder kafkaOutBuilder;
//    private final Endpoint backEndRequest;
//    private final Endpoint backEndResponse;
    
    /**
     * Constructor with constructor injection of the Camel context.
     * @param camelContext
     */
    public SmsGatewayConfiguration(CamelContext camelContext, 
            @Value("{{sms.gateway.kafka.brokers}}") String kafkaBrokers,
            @Value("{{sms.gateway.kafka.inbound-topic}}") String kafkaInboundTopic,
            @Value("{{sms.gateway.kafka.outbound-topic}}") String kafkaOutboundTopic,
            @Value("{{sms.gateway.kafka.client-id}}") String kafkaClientId,
            @Value("{{sms.gateway.kafka.group-id}}") String kafkaGroupId) {
        LOG.debug("Constructing SmsGatewayConfiguration with CamelContext '{}'", camelContext.getName());
        this.camelContext = camelContext;

        kafkaInBuilder = StaticEndpointBuilders
                .kafka(kafkaInboundTopic)
                .brokers(kafkaBrokers)
                .groupId(kafkaGroupId);
 
        kafkaOutBuilder = StaticEndpointBuilders
                .kafka(kafkaOutboundTopic)
                .brokers(kafkaBrokers)
                .clientId(kafkaClientId);
    }
    
    @Bean(Constants.ENDPOINT_FRONTEND_REQUEST)
    public Endpoint frontEndRequestEndpoint() {
        return kafkaInBuilder.resolve(camelContext);
    }

    @Bean(Constants.ENDPOINT_FRONTEND_RESPONSE)
    public Endpoint frontEndResponseEndpoint() {
        return kafkaOutBuilder.resolve(camelContext);
    }
    
    /**
     * Defines string constants (field names etc)
     */
    public static final class Constants {
        
        // Our endpoint names in the registry
        
        public static final String ENDPOINT_FRONTEND_REQUEST = "frontEndRequest";
        public static final String ENDPOINT_FRONTEND_RESPONSE = "frontEndResponse";
        public static final String ENDPOINT_BACKEND_REQUEST = "backEndRequest";
        public static final String ENDPOINT_BACKEND_RESPONSE = "backEndResponse";
        
        // Incoming message fields
        
        public static final String IN_FIELD_CORREL_ID = "correl-id";
        public static final String IN_FIELD_CLIENT_ID = "client-id";
        public static final String IN_FIELD_DEADLINE = "deadline";
        public static final String IN_FIELD_PAYLOAD = "payload";

        // Outgoing message fields
        
        public static final String OUT_FIELD_CORREL_ID = "correl-id";
        public static final String OUT_FIELD_CLIENT_ID = "client-id";
        public static final String OUT_FIELD_SMS_STATUS = "sms-status";
        public static final String OUT_FIELD_ERROR_TEXT = "error-text";
        
        // Values for OUT_FIELD_SMS_STATUS
        
        public static final String SMS_STATUS_SENT = "SENT";
        public static final String SMS_STATUS_DELIVERED = "DELIVERED";
        public static final String SMS_STATUS_EXPIRED = "EXPIRED";
        public static final String SMS_STATUS_FAILED = "FAILED";        // failed now but may go
        public static final String SMS_STATUS_INVALID = "INVALID";      // will never go
    }
}
