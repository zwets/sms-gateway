package it.zwets.sms.gateway.comp;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import it.zwets.sms.gateway.dto.CorrelationRecord;


/**
 * Manages the mapping of recall-id to client-id,correl-id.
 */
public class CorrelationTable {

    private static final Logger LOG = LoggerFactory.getLogger(CorrelationTable.class);

    private Map<String, CorrelationRecord> map = new HashMap<>();

    public void store(CorrelationRecord rec) {
        if (rec.recallId() == null) {
            LOG.error("Refusing to store correlation record for recall-id null");
        }
        else {
            LOG.debug("Add/replace correlation record {} -> {}:{}", rec.recallId(), rec.clientId(), rec.correlId());
            map.put(rec.recallId(), rec);
        }
    }

    public void store(String recallId, String clientId, String correlId) {
        store(new CorrelationRecord(recallId, clientId, correlId));
    }

    public CorrelationRecord fetch(String recallId) {
        return map.get(recallId);
    }
}
