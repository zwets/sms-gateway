package it.zwets.sms.gateway;

import org.apache.camel.CamelContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Central service API for the SMS gateway.
 */
@Service
public class SmsGatewayService {
    
    private static final Logger LOG = LoggerFactory.getLogger(SmsGatewayService.class);

    private final CamelContext camelContext;
    
    public SmsGatewayService(CamelContext camelContext) {
        LOG.debug("Creating the SmsGatewayService");
        this.camelContext = camelContext;
    }
}
