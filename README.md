# sms-gateway

_Secure and reliable delivery of SMS Messages_


## Background

This is the back-end component for the [SMS Scheduler](https://github.com/zwets/sms-scheduler).
It takes care of the communication between the scheduler and the SMS Centre.

The particular feature of SMS Scheduler and SMS Gateway (see the 
[background section in the SMS Scheduler README](https://github.com/zwets/sms-scheduler#background))
is that SMS messages (content _and_ recipient number) are securely
encrypted throughout the system.

This is established using PKI: the _client_ uses the public key of the
SMS Gateway to encrypt payloads before handing these to the SMS Scheduler
for (future) delivery.

When the message is due, the scheduler passes the encrypted payload to
the SMS Gateway, which takes care of delivery to the SMSC.  Only at this
point is the payload decrypted.

The idea of this setup is that the Scheduler (which is highly stateful
as it holds the scheduled messages and handles failures and retries) has
no access whatsoever to the message content or recipient.

The SMS Gateway, or the other hand, holds no state: it consumes encrypted
blobs from the scheduler (that were encrypted for it by the _client_ of
the scheduler), temporarily decrypts these and forwards them to the SMSC.
It asynchronously reports back status changes to the scheduler.

> **IMPORTANT:** as is [mentioned](https://camel.apache.org/components/next/smpp-component.html)
> [elsewhere](https://github.com/opentelecoms-org/smsrouter/blob/master/README.md),
> SMS is notoriously unreliable and insecure once it enters the Network.
> What SMS Scheduler and Gateway attempt to achieve is maximum reliability
> and confidentiality _up to the moment the SMS reaches the provider_.


## Implementation

The SMS Gateway consumes from an incoming Kafka topic `send-sms` and
produces status updates on an outgoing topic `sms-status`.  It is built on
top of [Apache Camel](https://camel.apache.org).

### Request

Request messages on topic `send-sms` must have this JSON structure:

```json
{
    "client-id": the ID of the client (tenant), e.g. "test",
    "correl-id": unique ID assigned by the client, to correlate response
    "deadline": timestamp before which the message must be sent
    "payload": the encrypted message
}
```

The payload, when decrypted, has the classic structure: one or more
headers, followed by an empty line, followed by the body:

    To: 31629845432
    Sender: name or number

    Here is the content.

At least the `To` header and body must be present.  Other headers may be
required depending on backend; excess headers are ignored.  The `SmsMessage`
class captures this logic.

#### Encryption

Encryption and decryption work as follows:

 * The SMS Gateway has a (configurable) "vault", a Java keystore that holds
   a key pair for each client
 * The [sms-crypto](https://github.com/zwets/sms-crypto) CLI tool can be
   used to manage the vaults
 * The SMS Gateway comes with a default vault packaged in the JAR; this
   vault has a key pair for the 'test' client (see [Testing](#testing) below)
 * For integration testing and production, either copy the default vault or
   create a new vault, add a key pair for every client, then
   * Place the vault in a non-world-readable place on the file system
   * Configure its location in the application properties for your profile
   (see [Configuration](#configuration) below)
 * To connect a client, retrieve the public key for that client from your
   vault with the `sms-crypto` CLI tool, and make the client encrypt the
   payload in each request with that public key (see the `PkiUtils` class).

### Response

Response messages on topic `sms-status` have this JSON structure:

```json
{
    "client-id": client ID from request
    "correl-id": correlation ID from request
    "sms-status": SENT, DELIVERED, EXPIRED, INVALID, FAILED
    "recall-id": optional field with backend correlation
    "error-text": optional field with error description
}
```

#### Statuses

Statuses have the following meaning:

 * `SENT`: the message was successfully passed on to the SMSC
 * `DELIVERED`: the SMSC reported the messages as delivered
 * `EXPIRED`: message was not sent because the deadline had passed
 * `INVALID`: the request received was invalid (client error)
 * `FAILED`: the SMS could not be sent (server/backend error)

In the _happy flow_, the client should expect two responses for every request:
`SENT` and `DELIVERED`, in that order (but see below).

If you receive `EXPIRED` or `INVALID`, you will not receive further responses,
and can be certain that the message was not sent.  An `EXPIRED` request could
be retried with a new deadline.  An `INVALID` request indicates a client error
and hence cannot be submitted again.

When you receive `FAILED`, this means that _in all likelihood_ the message did
not reach the recipient, and the gateway will not retry further.  The `FAILED`
may come after `SENT` and implies non-delivery.

> In the current implementation you can expect to receive status updates in
> a sensible order, and to eventually receive at least one status response,
> but you should be prepared for out-of-order responses.
>
> **Note** we do not yet have `DELIVERED` notifications from the backend,
> and hence it may happen that you get no response at all.


## Running

See the `bin` directory.  @TODO@ add detail.

### Configuration

@TODO@: document the `application-*.properties` and setting up the vault
using <https://github.com/zwets/sms-client>


## Testing

@TODO@: describe the `dev` and `test` profiles and how to JUnit and live
testing (see the `test-client` directory).


## Developing

@TODO@: setting up Eclipse, running unit tests


## Implementation Notes

### Wasp API

The current release uses the Vodacom "Wasp" API, a simple http POST interface.
Its route and processors are defined separately and will need to be overridden
if you reuse this code.

### Failover & Retry

Though it can be used on its own, the SMS Gateway was designed in tandem with
the SMS Scheduler.  The Scheduler manages long-running processes and has all
facilities for dealing with backend failures, including scheduling retries.

For this reason, the SMS Gateway is configured for "fail-fast" operation: when
a backend connection is unavailable, it will attempt fail-over after a short
timeout, and without retries.

 * [On Exception](https://camel.apache.org/manual/exception-clause.html)
 * [Error Handler](https://camel.apache.org/manual/error-handler.html)
 * [Dead Letter Channel](https://camel.apache.org/components/4.0.x/eips/dead-letter-channel.html)
 * [Blog on Camel Retry Mechanisms](https://www.jessym.com/articles/retry-mechanisms-in-apache-camel)
 * [Failover EIP](https://camel.apache.org/components/4.0.x/eips/failover-eip.html)

### SMPP

 * <https://camel.apache.org/components/next/smpp-component.html>


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

