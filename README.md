# sms-gateway

_Secure and reliable delivery of SMS Messages_

**WORK IN PROGRESS**


## Background

This is the back-end component for the [SMS Scheduler](https://github.com/zwets/sms-scheduler).
It takes care of the communication between the scheduler and the SMS Centre.

The particular feature of SMS Scheduler and SMS Gateway (see the 
[background section in the SMS Scheduler README](https://github.com/zwets/sms-scheduler#background))
is that SMS messages (content and recipient number) are securely
encrypted throughout the system.

This is established using PKI: the client uses the public key of the
SMS Gateway to encrypt payloads before handing these to the SMS Scheduler
for (future) delivery.

When the message is due, the scheduler passes the encrypted payload to
the SMS Gateway, which takes care of delivery to the SMSC.  Only at this
point is the payload decrypted.

The idea of this setup is that the Scheduler (which is highly stateful
as it holds the scheduled messages and manages failures and retries) has
no access whatsoever to the message content, including recipient numbers.

The SMS Gateway, or the other hand, holds no state: it consumes encrypted
blobs from the scheduler (encrypted by the _client_ of the scheduler),
temporarily decrypts these and forwards them (over SMPP) to the SMSC.  It
asynchronously reports back status changes to the scheduler.

**IMPORTANT:** as is mentioned [elsewhere](https://camel.apache.org/components/next/smpp-component.html),
SMS is notoriously unreliable and insecure once it enters the SMS Network;
what SMS Scheduler and Gateway attempt to achieve is higher reliability
(by having extensive failure and retry handling in the scheduler), while
maintaining full confidentiality on the scheduler server.


## Implementation

The SMS Gateway consumes from an incoming Kafka topic `send-sms` and produces
status updates on an outgoing topic `sms-status`.  It is built on top of
[Apache Camel](https://camel.apache.org).

#### Request

Request messages on topic `send-sms` must have this JSON structure:

```json
{
    "client-id": the ID of the client (tenant), e.g. "test",
    "correl-id": unique ID assigned by the client, to correlate response
    "deadline": timestamp before which the message must be sent
    "payload": the encrypted message 
}
```

#### Response

Response messages on topic `sms-status` have this JSON structure:

```json
{
    "client-id": client ID from request
    "correl-id": correlation ID from request
    "error-text": optional field with error description
    "sms-status": SENT, DELIVERED, EXPIRED, FAILED, INVALID
}
```


## Running

@TODO@ see the `scripts` directory.


#### Licence

sms-gateway - Backend for the SMS Scheduler  
Copyright (C) 2023  Marco van Zwetselaar

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with this program.  If not, see <http://www.gnu.org/licenses/>.

