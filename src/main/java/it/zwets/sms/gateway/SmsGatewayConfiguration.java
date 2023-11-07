package it.zwets.sms.gateway;

import org.apache.camel.CamelContext;
import org.apache.camel.component.kafka.KafkaComponent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

/**
 * Application configuration.
 * 
 * Produces the beans needed by the application (if any).
 */
@Configuration(proxyBeanMethods = false)
public class SmsGatewayConfiguration {
    
    private static final Logger LOG = LoggerFactory.getLogger(SmsGatewayConfiguration.class);    

    @Value("${sms.gateway.kafka.inbound-topic}")
    private String inboundTopic;
    
    @Value("${sms.gateway.kafka.outbound-topic}")
    private String outboundTopic;

    private final CamelContext camelContext;

    /**
     * Constructor with constructor injection of the Camel context.
     * @param camelContext
     */
    public SmsGatewayConfiguration(CamelContext camelContext) {
        LOG.debug("Constructing SmsGatewayConfiguration with CamelContext '{}'", camelContext.getName());
        this.camelContext = camelContext;
    }
    
    /**
     * Defines string constants (field names etc)
     */
    public static final class Constants {
        
        // Incoming message fields
        
        public static final String IN_FIELD_CORREL_ID = "correl-id";
        public static final String IN_FIELD_CLIENT_ID = "client-id";
        public static final String IN_FIELD_DEADLINE = "deadline";
        public static final String IN_FIELD_MESSAGE = "message";

        // Outgoing message fields
        
        public static final String OUT_FIELD_CORREL_ID = "correl-id";
        public static final String OUT_FIELD_CLIENT_ID = "client-id";
        public static final String OUT_FIELD_SMS_STATUS = "sms-status";
        public static final String OUT_FIELD_ERROR_CODE = "error-code";
        public static final String OUT_FIELD_ERROR_TEXT = "error-text";
        
        // Values for OUT_FIELD_SMS_STATUS
        
        public static final String SMS_STATUS_RELAYED = "RELAYED";
        public static final String SMS_STATUS_DELIVERED = "DELIVERED";
        public static final String SMS_STATUS_EXPIRED = "EXPIRED";
        public static final String SMS_STATUS_FAILED = "FAILED";        // failed now but may go
        public static final String SMS_STATUS_INVALID = "INVALID";      // will never go
    }
}
