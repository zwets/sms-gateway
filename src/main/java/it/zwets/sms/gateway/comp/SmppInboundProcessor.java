package it.zwets.sms.gateway.comp;

import static it.zwets.sms.gateway.SmsGatewayConfiguration.Constants.HEADER_ERROR_TEXT;
import static it.zwets.sms.gateway.SmsGatewayConfiguration.Constants.HEADER_RECALL_ID;
import static it.zwets.sms.gateway.SmsGatewayConfiguration.Constants.HEADER_SMS_STATUS;
import static it.zwets.sms.gateway.SmsGatewayConfiguration.Constants.SMS_STATUS_DELIVERED;
import static it.zwets.sms.gateway.SmsGatewayConfiguration.Constants.SMS_STATUS_EXPIRED;
import static it.zwets.sms.gateway.SmsGatewayConfiguration.Constants.SMS_STATUS_FAILED;
import static it.zwets.sms.gateway.SmsGatewayConfiguration.Constants.SMS_STATUS_INVALID;
import static it.zwets.sms.gateway.SmsGatewayConfiguration.Constants.SMS_STATUS_SENT;

import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.component.smpp.SmppConstants;
import org.apache.camel.component.smpp.SmppMessage;
import org.jsmpp.util.DeliveryReceiptState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * Processes asynchronous delivery reports from the SMPP gateway.
 * 
 * Transforms the headers on the response coming from Camel SMPP to exchange
 * headers that the SMS route will translate to a response to the client.
 * 
 * When the <code>process</code> method has completed, the message header
 * sms-status is guaranteed to be set.  Does nothing if sms-status is already
 * set on entry.
 */
public class SmppInboundProcessor implements Processor {
 
    private static final Logger LOG = LoggerFactory.getLogger(SmppInboundProcessor.class);
 
    @Override
    public void process(Exchange exchange) throws Exception {

        Message msg = exchange.getIn();

        if (msg.getHeader(HEADER_SMS_STATUS) != null) {
            LOG.debug("Skipping, status already {}", msg.getHeader(HEADER_SMS_STATUS));
        }
        else {

            try {
                LOG.debug("Processing Inbound SMPP message");

                SmppMessage smppMsg = exchange.getIn(SmppMessage.class);

                if (smppMsg == null) {
                    throw new Exception("Inbound SMPP message is not an SmppMessage");
                }

                if (!smppMsg.isDeliveryReceipt() ) {
                    LOG.warn("Ignoring a {} message from the SMSC: {}",
                            smppMsg.getHeader(SmppConstants.MESSAGE_TYPE), smppMsg.getBody());
                    exchange.setRouteStop(true);
                }
                else {

                    DeliveryReceiptState state = smppMsg.getHeader(SmppConstants.FINAL_STATUS, DeliveryReceiptState.class);
                    String recallId = smppMsg.getHeader(SmppConstants.ID, String.class); // string (on send is a list)
                    String error = smppMsg.getHeader(SmppConstants.ERROR, String.class); // null or smsc specific
//                  Date doneDate = smppMsg.getHeader(SmppConstants.DONE_DATE, Date.class); // when attained final state
//                  Date submitDate = smppMsg.getHeader(SmppConstants.SUBMIT_DATE, Date.class); // when message was submitted / replaced
//                  Integer submitted = smppMsg.getHeader(SmppConstants.SUBMITTED, Integer.class); // the number submitted when distribution list
//                  Integer delivered = smppMsg.getHeader(SmppConstants.DELIVERED, Integer.class); // the number delivered when distribution list

                    if (recallId != null) {

                        if (recallId.startsWith("0")) {

                            // HACK: the Voda SMSC sends back a 0-padded number, whereas the message ID it gave us
                            // at time of submission was not zero-padded.  So here (despite the ID being a String
                            // according to the SMPP spec) we strip the leading zeros.

                            LOG.info("Hack: stripping leading zeros off the recall-id {}", recallId);
                            recallId = recallId.replaceFirst("^0+", "");
                        }

                        LOG.info("Delivery receipt for recall-id {}: {} (error {})", recallId, state, error);
                        msg.setHeader(HEADER_RECALL_ID, recallId);
                    }
                    else {
                        LOG.error("Delivery receipt without recall ID, will process but can't report back to client");
                    }

                    if (LOG.isDebugEnabled()) {
                        LOG.debug("Optional parameters received");
                        @SuppressWarnings("unchecked")
                        Map<Short,Object> opts = smppMsg.getHeader(SmppConstants.OPTIONAL_PARAMETER, Map.class);
                        if (opts != null) {
                            for (Map.Entry<Short, Object> entry : opts.entrySet()) {
                                LOG.debug(" - {} -> {}", entry.getKey(), entry.getValue());
                            }
                        }
                    }

                    switch (state) {
                    case DeliveryReceiptState.ACCEPTD:
                    case DeliveryReceiptState.ENROUTE:
                        msg.setHeader(HEADER_SMS_STATUS, SMS_STATUS_SENT);
                        break;
                    case DeliveryReceiptState.DELIVRD:
                        msg.setHeader(HEADER_SMS_STATUS, SMS_STATUS_DELIVERED);
                        break;
                    case DeliveryReceiptState.EXPIRED:
                        msg.setHeader(HEADER_SMS_STATUS, SMS_STATUS_EXPIRED);
                        break;
                    case DeliveryReceiptState.DELETED:
                        msg.setHeader(HEADER_SMS_STATUS, SMS_STATUS_FAILED);
                        msg.setHeader(HEADER_ERROR_TEXT, "Message was deleted");
                        break;
                    case DeliveryReceiptState.UNKNOWN: // not sure, maybe send no response @TODO@
                        msg.setHeader(HEADER_SMS_STATUS, SMS_STATUS_FAILED);
                        msg.setHeader(HEADER_ERROR_TEXT, "SMSC delivery state UNKNOWN: %s".formatted(error != null ? error : "(no error message)"));
                        break;
                    case DeliveryReceiptState.UNDELIV:
                        msg.setHeader(HEADER_ERROR_TEXT, "Message was undeliverable: %s".formatted(error != null ? error : "(no error message)"));
                        msg.setHeader(HEADER_SMS_STATUS, SMS_STATUS_FAILED);
                        break;
                    case DeliveryReceiptState.REJECTD:
                        msg.setHeader(HEADER_ERROR_TEXT, "SMSC rejects message: %s".formatted(error != null ? error : "(no error message)"));
                        msg.setHeader(HEADER_SMS_STATUS, SMS_STATUS_INVALID);
                        break;
                    default:
                        LOG.error("Unknown DeliveryReceiptState: ", state);
                        msg.setHeader(HEADER_SMS_STATUS, SMS_STATUS_FAILED);
                        msg.setHeader(HEADER_ERROR_TEXT, "Unknown delivery state: %s: %s".formatted(state, error != null ? error : "(no error message)"));
                    }
                }
            }
            catch (Exception e) {
                LOG.error("Exception while processing SMPP response: {}", e.getMessage());
                msg.setHeader(HEADER_SMS_STATUS, SMS_STATUS_FAILED);
                msg.setHeader(HEADER_ERROR_TEXT, e.getMessage());
            }
        }
    }
}
