package it.zwets.sms.gateway.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonRootName;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;

@JsonRootName("SMSSubmitReq")
public class VodaRequest {

    public record Sender(
            @JsonProperty String Username,
            @JsonProperty String Password,
            @JsonProperty Integer SenderAddressType,      // 1
            @JsonProperty String Address) { }
    
    public record Recipient(
            @JsonProperty String Number) { }              // 255XXXXXXXXX
    
    public record MsgDetails(
            @JsonProperty String ShortMessage,
            @JsonProperty Integer MsgType) { }            // 0
    
    public record Tariff(
            @JsonProperty Integer TariffPrice) { }        // 0
    
    public record DeliveryReport(
            @JsonProperty Boolean ReportEnabled) { }

    @JsonProperty String InterfaceID;
    @JsonProperty Sender Sender;
    @JsonProperty Recipient Recipient;
    @JsonProperty MsgDetails MsgDetails;
    @JsonProperty Tariff Tariff;
    @JsonProperty DeliveryReport DeliveryReport;
    
    public VodaRequest(String userName, String password, String sender, String recipient, String message)
    {
        InterfaceID = "JX";
        Sender = new Sender(userName, password, 1, sender);
        Recipient = new Recipient(recipient);
        MsgDetails = new MsgDetails(message, 0);
        Tariff = new Tariff(0);
        DeliveryReport = new DeliveryReport(false);
    }
    
    @Override
    public String toString() {
        try {
            return new XmlMapper().writeValueAsString(this);
        } catch (JsonProcessingException e) {
            return "SERIALIZATION FAILED";
        }
    }
}
