package it.zwets.sms.gateway;

import javax.net.ssl.HostnameVerifier;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.builder.endpoint.StaticEndpointBuilders;
import org.apache.camel.builder.endpoint.dsl.KafkaEndpointBuilderFactory.KafkaEndpointConsumerBuilder;
import org.apache.camel.builder.endpoint.dsl.KafkaEndpointBuilderFactory.KafkaEndpointProducerBuilder;
import org.apache.hc.client5.http.ssl.NoopHostnameVerifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import it.zwets.sms.crypto.Vault;
import it.zwets.sms.gateway.comp.PayloadDecoder;
import it.zwets.sms.gateway.comp.RequestProcessor;
import it.zwets.sms.gateway.comp.SmppRequestProducer;
import it.zwets.sms.gateway.comp.SmppResponseProcessor;
import it.zwets.sms.gateway.comp.VodaWaspRequestProducer;
import it.zwets.sms.gateway.comp.VodaWaspResponseProcessor;
import it.zwets.sms.gateway.routes.SmppRoute;
import it.zwets.sms.gateway.routes.VodaWaspRoute;

/**
 * Application configuration.
 * 
 * Produces the beans needed by the application (if any).
 */
@Configuration(proxyBeanMethods = false)
public class SmsGatewayConfiguration {
    
    private static final Logger LOG = LoggerFactory.getLogger(SmsGatewayConfiguration.class);

    public static final String BACKEND_SMPP = "SMPP";
    public static final String BACKEND_WASP = "WASP";
    
    private final CamelContext camelContext;

    private final String[] allowedClients;
    private final String clientLogDir;
    private final String vaultKeystore;
    private final String vaultPassword;
    private final KafkaEndpointConsumerBuilder kafkaInBuilder;
    private final KafkaEndpointProducerBuilder kafkaOutBuilder;
    private final String backend;
    private final String waspUsername;
    private final String waspPassword;
    
    /**
     * Constructor with constructor injection of the Camel context and
     * endpoint properties.
     * 
     * Note the {{ are Camel endpoint placeholders, the ${ are Spring.
     * 
     * @param camelContext
     */
    public SmsGatewayConfiguration(CamelContext camelContext,
            @Value("${sms.gateway.allowed-clients}") String allowClients,
            @Value("${sms.gateway.client-log.dir}") String clientLog,
            @Value("${sms.gateway.crypto.keystore}") String keyStore,
            @Value("${sms.gateway.crypto.storepass}") String storePass,
            @Value("${sms.gateway.kafka.brokers}") String kafkaBrokers,
            @Value("${sms.gateway.kafka.inbound-topic}") String kafkaInboundTopic,
            @Value("${sms.gateway.kafka.outbound-topic}") String kafkaOutboundTopic,
            @Value("${sms.gateway.kafka.client-id}") String kafkaClientId,
            @Value("${sms.gateway.kafka.group-id}") String kafkaGroupId,
            @Value("${sms.gateway.backend:SMPP}") String backend, // BACKEND_SMPP or BACKEND_WASP
            @Value("${sms.gateway.vodacom.wasp.username}") String vodaWaspUsername, 
            @Value("${sms.gateway.vodacom.wasp.password}") String vodaWaspPassword
            ) {
        LOG.debug("Constructing SmsGatewayConfiguration to {} backend  with CamelContext '{}'", backend, camelContext.getName());
        this.camelContext = camelContext;

        allowedClients = allowClients.split(" *, *");
        
        clientLogDir = clientLog;
        
        vaultKeystore = keyStore;
        vaultPassword = storePass;
        
        kafkaInBuilder = StaticEndpointBuilders
                .kafka(kafkaInboundTopic)
                .brokers(kafkaBrokers)
                .groupId(kafkaGroupId);
 
        kafkaOutBuilder = StaticEndpointBuilders
                .kafka(kafkaOutboundTopic)
                .brokers(kafkaBrokers)
                .clientId(kafkaClientId);
        
        this.backend = backend == null ? SmsGatewayConfiguration.BACKEND_SMPP : backend;
        
        waspUsername = vodaWaspUsername;
        waspPassword = vodaWaspPassword;
    }
    
    @Bean(Constants.ENDPOINT_FRONTEND_REQUEST)
    public Endpoint frontEndRequestEndpoint() {
        return kafkaInBuilder.resolve(camelContext);
    }

    @Bean(Constants.ENDPOINT_FRONTEND_RESPONSE)
    public Endpoint frontEndResponseEndpoint() {
        return kafkaOutBuilder.resolve(camelContext);
    }
    
    @Bean(Constants.ENDPOINT_BACKEND_REQUEST)
    public Endpoint backendRequestEndpoint() {
        switch (backend.toUpperCase()) {
        case SmsGatewayConfiguration.BACKEND_SMPP:
            return camelContext.getEndpoint(SmppRoute.SMPP_ROUTE);
        case SmsGatewayConfiguration.BACKEND_WASP:
            return camelContext.getEndpoint(VodaWaspRoute.VODA_WASP_ROUTE);
        default:
            throw new IllegalArgumentException("Not a valid backend: %s".formatted(backend));
        }
    }

    @Bean(Constants.ENDPOINT_CLIENT_LOG)
    public Endpoint clientLogEndpoint() {
        return camelContext.getEndpoint("file://%s?fileExist=append".formatted(clientLogDir));
    }

    @Bean("NoopHostnameVerifier")
    public HostnameVerifier getNoopHostnameVerifier() {
        return NoopHostnameVerifier.INSTANCE;
    }
   
    @Bean RequestProcessor getRequestProcessor() {
        return new RequestProcessor(allowedClients);
    }

    @Bean
    public SmppRequestProducer getSmppRequestProducer() {
        return new SmppRequestProducer();
    }
    
    @Bean
    public SmppResponseProcessor getSmppResponseProcessor() {
        return new SmppResponseProcessor();
    }

    @Bean
    public VodaWaspRequestProducer getVodaWaspRequestProducer() {
        return new VodaWaspRequestProducer(waspUsername, waspPassword);
    }
    
    @Bean
    public VodaWaspResponseProcessor getVodaWaspResponseProcessor() {
        return new VodaWaspResponseProcessor();
    }
    
    @Bean
    public Vault getVault() {
        return new Vault(vaultKeystore, vaultPassword);
    }
    
    @Bean
    public PayloadDecoder getPayloadDecoder(Vault vault) {
        return new PayloadDecoder(vault);
    }
    
    /**
     * Defines string constants (field names etc)
     */
    public static final class Constants {
        
        // Our endpoint names in the registry
        
        public static final String ENDPOINT_FRONTEND_REQUEST = "frontEndRequest";
        public static final String ENDPOINT_FRONTEND_RESPONSE = "frontEndResponse";
        public static final String ENDPOINT_BACKEND_REQUEST = "backEndRequest";
        public static final String ENDPOINT_CLIENT_LOG = "clientLog";
        
        // Incoming message fields
        
        public static final String IN_FIELD_CLIENT_ID = "client-id";
        public static final String IN_FIELD_CORREL_ID = "correl-id";
        public static final String IN_FIELD_DEADLINE = "deadline";
        public static final String IN_FIELD_PAYLOAD = "payload";

        // Message headers while processing

        public static final String HEADER_CLIENT_ID = "clientId";
        public static final String HEADER_CORREL_ID = "correlId";
        public static final String HEADER_TIMESTAMP = "timeStamp";
        public static final String HEADER_SMS_STATUS = "smsStatus";
        public static final String HEADER_RECALL_ID = "recallId";
        public static final String HEADER_ERROR_TEXT = "errorText";

        // Outgoing message fields
        
        public static final String OUT_FIELD_CLIENT_ID = "client-id";
        public static final String OUT_FIELD_CORREL_ID = "correl-id";
        public static final String OUT_FIELD_TIMESTAMP = "timestamp";
        public static final String OUT_FIELD_SMS_STATUS = "sms-status";
        public static final String OUT_FIELD_RECALL_ID = "recall-id";
        public static final String OUT_FIELD_ERROR_TEXT = "error-text";
        
        // Values for OUT_FIELD_SMS_STATUS
        
        public static final String SMS_STATUS_SENT = "SENT";
        public static final String SMS_STATUS_DELIVERED = "DELIVERED";
        public static final String SMS_STATUS_EXPIRED = "EXPIRED";
        public static final String SMS_STATUS_FAILED = "FAILED";        // failed now but may go
        public static final String SMS_STATUS_INVALID = "INVALID";      // will never go
        
        // SMS headers
        
        public static final String SMS_HEADER_TO = "To";
        public static final String SMS_HEADER_SENDER = "Sender";
    }
}
