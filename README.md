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

The SMS Gateway, on the other hand, holds no state: it consumes encrypted
blobs from the scheduler (that were encrypted for it by the _client_ of
the scheduler), temporarily decrypts these and forwards them to the SMSC.
It asynchronously reports back status changes to the scheduler.

> **IMPORTANT:** as is [mentioned](https://camel.apache.org/components/next/smpp-component.html)
> [elsewhere](https://github.com/opentelecoms-org/smsrouter/blob/master/README.md),
> SMS is notoriously unreliable and insecure once it enters the Network.
> What SMS Scheduler and Gateway attempt to achieve is maximum reliability
> and confidentiality _up to the moment the SMS reaches the provider_.


## Interface

The SMS Gateway consumes from an incoming Kafka topic `send-sms` and
produces status updates on an outgoing topic `sms-status`.  It is built on
top of [Apache Camel](https://camel.apache.org).

### Request

Request messages on topic `send-sms` must have this JSON structure:

```
{
    "client-id": the ID of the client (tenant), e.g. "test",
    "correl-id": unique ID assigned by the client, to correlate response
    "deadline":  timestamp before which the message must be sent
    "payload":   the encrypted message
}
```

The payload field, when decrypted, has the classic structure: one or more
headers, followed by an empty line, followed by the body:

    To: +31629845432
    Sender: name or number

    Here is the content.

At least the `To` header and body must be present.  The value of the `To`
header must be a full international number with the `+` prefix.  Other
headers may be required depending on backend.  Excess headers are ignored.

### Encryption

Encryption and decryption work as follows:

 * The SMS Gateway has a (configurable) "vault", a Java keystore that holds
   a key pair for each client
 * The [sms-client](https://github.com/zwets/sms-client) CLI tool can be
   used to manage vaults
 * The SMS Gateway comes with a default vault packaged in the JAR.  This
   vault has a key pair for the 'test' client (see [Testing](#testing) below)
 * For integration testing and production, either copy the default vault or
   create a new vault, add a key pair for every client, then
   * Place the vault in a non-world-readable place on the file system
   * Configure its location in the application properties for your profile
   (see [Configuration](#configuration) below)
 * To connect a client, give it its public key (retrieved from the vault with
   the `sms-client` CLI tool), and make the client encrypt the payload in each
   request with that public key.
   * Support code for encrypting is in the PkiUtils class in `sms-client`

### Response

Response messages on topic `sms-status` have this JSON structure:

```
{
    "client-id":  client ID from request
    "correl-id":  correlation ID from request
    "timestamp":  ISO-8601 instant the message was processed
    "sms-status": SENT, DELIVERED, EXPIRED, INVALID, FAILED
    "recall-id":  optional field with backend correlation
    "error-text": optional field with error description
}
```

#### Statuses

Statuses have the following meaning:

 * `SENT`: the message was successfully passed on to the SMSC
 * `DELIVERED`: the SMSC reported the messages as delivered
 * `EXPIRED`: message was not sent because the deadline had passed
 * `INVALID`: the request received was invalid (client error)
   or was deemed undeliverable by an SMSC downstream from ours.
 * `FAILED`: the SMS could not be sent (server/backend error)

In the _happy flow_, the client should expect two responses for every request:
`SENT` and `DELIVERED`, in that order (but see below).

If you receive `EXPIRED` or `INVALID`, you will not receive further responses,
and can be certain that the message was not sent.  An `EXPIRED` request could
be retried with a new deadline.  An `INVALID` request indicates a client error
and hence will most likely not succeed on another attempt.

When you receive `FAILED`, this means that _in all likelihood_ the message did
not reach the recipient, and the gateway will not retry further.  The `FAILED`
may come after `SENT` and implies non-delivery.

> In the current implementation you can expect to receive status updates in
> a sensible order, and to eventually receive at least one status response,
> but you should be prepared for out-of-order responses.  Not all backends
> give `DELIVERED` notifications.


## Running

See the scripts in the `bin` directory, and [deployment](#deployment) below.

 * `run-boot-test.sh` (development) runs code directly with `test` profile
 * `run-gateway-test.sh` runs the JAR with the `test` profile activated

### Configuration

Drop a file `application.properties` (or separate files
`application-{prod,test}.properties`) in your PWD or PWD/config, and these
will be picked up.

The Spring Boot docs for
[Externalised Configuration](https://docs.spring.io/spring-boot/docs/3.0.4/reference/html/features.html#features.external-config)
have all the details.


## Testing

Requests submitted by the `test` client are not routed to the backend but
instead "loop back" with behaviour determined by the SMS body.  For instance,
sending a message with `S1D0` in the body will return a `SENT` reply but no
`DELIVERED`.  See the route in `TestClientRoute` for details.

The unit tests in `src/test/java` exercise the `test` client routes, but it
will also work in production (whenever the `test` client submits).  You can
You can specify response delays in `application-*.properties`.

The `client` directory has scripts for generating `send-sms` requests
and injecting them on the `send-sms` queue, generating "fake" reponses on
the `sms-status` queue, and for monitoring the `send-sms` and `sms-status`
queues.  (Requires installation of `kafkacat` or `kcat`, and `jq`.)

See [client/README.md](client/README.md) for details.


## Deployment

Settings for the steps below

    SRC_DIR=$PWD               # path to the unpacked repository
    TGT_DIR=/opt/sms-gateway   # installation directory

Deployment requires [sms-client](https://github.com/zwets/sms-client)
for creating the vault.  Install as described in that repository.

Create the installation directory

    sudo mkdir $TGT_DIR &&
    sudo cp /path/to/downloaded/sms-gateway-${VERSION}.jar $TGT_DIR &&
    sudo ln -sf sms-gateway-${VERSION}.jar $TGT_DIR/sms-gateway.jar

Create the `smeg` user and group it will run as

    sudo adduser --system --gecos 'SMS Gateway' --group --no-create-home --home $TGT_DIR smeg

Create config dir

    sudo mkdir $TGT_DIR/config &&
    sudo chown root:smeg $TGT_DIR/config &&
    sudo chmod 0750 $TGT_DIR/config

Create directory for per-client logs (note that the application logs to
Systemd journal: `journalctl -eu sms-gateway`)

    sudo mkdir /var/log/sms-gateway &&
    sudo chown smeg:adm /var/log/sms-gateway &&
    sudo chmod 0750 /var/log/sms-gateway

Add application properties to config (from the one in the repo)

    sudo install -m 0640 -o root -g smeg -t /opt/sms-gateway/config \
        $SRC_DIR/src/main/resources/application-prod.properties &&
    sudo edit /opt/sms-gateway/config/application-prod.properties
        # set the appropriate parameters to override defaults

Create the prod vault (from the builtin.vault in the repo)

    sudo install -m 0640 -o root -g smeg -T \
         $SRC_DIR/src/main/resources/builtin.vault $TGT_DIR/config/prod.vault

    # Change the store password from 123456 to something of your own
    NEW_PASS='...'
    keytool -storepasswd -keystore $TGT_DIR/config/prod.vault -storepass 123456 -new "$NEW_PASS"

Add a key pair for each of your clients

    GEN_PAIR=/opt/sms-client/bin/new-keypair
    for CLIENT in client1 client2 ...; do
        sudo $GEN_PAIR $TGT_DIR/config/prod.vault "$NEW_PASS" "$CLIENT"
    done

(Optional but very convenient) extract the public keys for later use

    SMS_CLIENT=/opt/sms-client/bin/sms-client
    sudo $SMS_CLIENT aliases $TGT_DIR/config/prod.vault "$NEW_PASS" | while read ALIAS; do
        sudo $SMS_CLIENT pubkey $TGT_DIR/config/prod.vault "$NEW_PASS" $ALIAS |
        sudo tee $TGT_DIR/config/$ALIAS.pub
    done

Set vault location and password in application properties

    sudo vi $TGT_DIR/config/application-prod.properties
    sms.gateway.crypto.keystore=config/prod.vault
    sms.gateway.crypto.storepass=${NEW_PASS}

Create the Kafka topics

    BOOTSTRAP_SERVER='localhost:9092'
    for TOPIC in send-sms sms-status correl-id; do
        kafka-topics.sh --bootstrap-server "${BOOTSTRAP_SERVER}" --create --if-not-exists --topic "${TOPIC}"
    done

Add the systemd service by editing `etc/sms-gateway.service` and copying or
symlinking into `/etc/systemd/system`

    systemctl enable --now $PWD/etc/sms-gateway.service

To see and follow the logging output

    sudo journalctl -xeu sms-gateway
    sudo journalctl -fu sms-gateway


## Implementation Notes

### Failover & Retry

Though it can be used on its own, the SMS Gateway was designed in tandem with
the SMS Scheduler.  The Scheduler manages long-running processes and has all
facilities for dealing with backend failures, including scheduling retries.

For this reason, the SMS Gateway is configured for "fail-fast" operation: when
a backend connection is unavailable, it will attempt fail-over after a short
timeout and without retries.

With the SMPP backend, it retries connecting every 12s @TODO@ consider aborting.

Related Camel docs:

 * [On Exception](https://camel.apache.org/manual/exception-clause.html)
 * [Error Handler](https://camel.apache.org/manual/error-handler.html)
 * [Dead Letter Channel](https://camel.apache.org/components/4.0.x/eips/dead-letter-channel.html)
 * [Blog on Camel Retry Mechanisms](https://www.jessym.com/articles/retry-mechanisms-in-apache-camel)
 * [Failover EIP](https://camel.apache.org/components/4.0.x/eips/failover-eip.html)

### SMPP

 * [Camel SMPP](https://camel.apache.org/components/next/smpp-component.html), wraps
 * [JSMPP](https://jsmpp.org) ([GitHub](https://github.com/opentelecoms-org/jsmpp))
 * JSMPP documentation on [their Wiki](https://github.com/opentelecoms-org/jsmpp/wiki/),
   and they have examples, including
   * [SMSC Simulator](https://github.com/opentelecoms-org/jsmpp/wiki/GettingStarted#running-smpp-server)
   * [SMS Router](http://smsrouter.org/) ([GitHub](https://github.com/opentelecoms-org/smsrouter))
     made by JSMPP developers; very simple Camel route, but apparently does the job.


#### Licence

sms-gateway - Backend for the SMS Scheduler  
Copyright (C) 2023-2025  Marco van Zwetselaar

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

