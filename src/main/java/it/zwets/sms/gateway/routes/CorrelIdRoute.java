package it.zwets.sms.gateway.routes;

import static it.zwets.sms.gateway.SmsGatewayConfiguration.Constants.BEAN_CORRELATION_TABLE;
import static it.zwets.sms.gateway.SmsGatewayConfiguration.Constants.HEADER_CORREL_REC;
import static it.zwets.sms.gateway.SmsGatewayConfiguration.Constants.HEADER_RECALL_ID;
import static org.apache.camel.LoggingLevel.DEBUG;

import org.apache.camel.Endpoint;
import org.apache.camel.EndpointInject;
import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.kafka.KafkaConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import it.zwets.sms.gateway.SmsGatewayConfiguration.Constants;
import it.zwets.sms.gateway.comp.CorrelationTable;
import it.zwets.sms.gateway.dto.CorrelationRecord;

/**
 * Camel route that collects mappings from backend recall-id to frontend correl-id.
 * 
 * This component solves the issue that we cannot pass the correl-id we receive from
 * the client on to the SMPP backend.  Instead, the SMPP backend gives us its own
 * correlation ID (which we call recall-id to avoid confusion) when we submit an SMS.
 * 
 * So, we need to keep a mapping from recall-id -> (client,correl-id).  The
 * {@link CorrelationTable} holds this in memory, and this component manages its
 * persistence by writing the entries on a Kafka topic, and reading them all off
 * in the correlation table (seekTo=BEGINNING on correlRead) at startup.
 */
@Component
public class CorrelIdRoute extends RouteBuilder {

    private static final Logger LOG = LoggerFactory.getLogger(CorrelIdRoute.class);
    
    public static final String CORREL_STORE = "direct:correl-store";

    @EndpointInject(Constants.ENDPOINT_CORREL_READ)
    private Endpoint correlRead;
    
    @EndpointInject(Constants.ENDPOINT_CORREL_WRITE)
    private Endpoint correlWrite;
    
    private static final String STASH = "stash";

    @Override
    public void configure() throws Exception {
        
        // Global exception handler for this route is to ignore but log errors
        onException(Throwable.class).routeId("correl-route-exception")
            .log(LoggingLevel.ERROR, LOG, "Ignoring exception in CorrelId Route: ${exception}")
            .handled(true);

        // Reads the CorrelationRecords off the correl-id topic and stores them in the
        // CorrelationTable.  The KafkaConsumer has SeekTo=BEGINNING, so the table is filled
        // with the whole (7 day) content of the Kafka topic at startup, then updates with
        // CorrelationRecord pushed onto the topic through CORREL_STORE below.
        from (correlRead).routeId("correl-read")
            .unmarshal()
            .json(CorrelationRecord.class)
            .bean(BEAN_CORRELATION_TABLE, "store");

        // Writes the CorrelationRecord (if any) in the CORREL_REC header to the topic.
        // The submission route in SmppRoute will have put it there upon submission of
        // a new SMS.
        from (CORREL_STORE).routeId("correl-store")
            .filter(header(HEADER_CORREL_REC).isNotNull())
            .setHeader(STASH, body())
            .setHeader(KafkaConstants.KEY, header(HEADER_RECALL_ID))
            .setBody(header(HEADER_CORREL_REC))
            .marshal().json()
            .log(DEBUG, LOG, "Writing correlation record for ${header.%s} to topic".formatted(HEADER_RECALL_ID))
            .to(correlWrite)
            .setBody(header(STASH))
            .removeHeader(STASH);
    }
}
