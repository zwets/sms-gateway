package it.zwets.sms.gateway.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonRootName;

// <SMSSubmitRsp>
//    <MessageID></MessageID>
//    <Status>
//        <StatusCode>4</StatusCode>
//        <StatusText>Error</StatusText>
//        <Detail>Error: Sender Address -> AFYA YAKO  is either not approved or not registered to WASP.</Detail>
//    </Status>
//</SMSSubmitRsp>

// <SMSSubmitRsp>
//    <MessageID>AE097237C8504D2DB771D6281D857539</MessageID>
//    <Status>
//       <StatusCode>0</StatusCode>
//       <StatusText>No Error</StatusText>
//       <Detail>Accepted</Detail>
//    </Status>
// </SMSSubmitRsp>


@JsonRootName("SMSSubmitRsp")
public class VodaWaspResponse {

    public record Status(
            @JsonProperty Integer StatusCode,
            @JsonProperty String StatusText,
            @JsonProperty String Detail) { }
    
    @JsonProperty public String MessageID;
    @JsonProperty public Status Status;
}
