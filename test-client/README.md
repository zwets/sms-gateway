# test-client README

This directory has tools to inject requests on the `send-sms` queue and
fake responses on the `sms-status` queue, and tools to monitor the live
queues.

To make testing convenient, drop the public key files for each test client
in the `lib/keys` directory.  These can be obtained from a vault with the
[sms-client](https://github.com/zwets/sms-client) tool:

    SMS_CLIENT=/path/to/sms-client/bin/sms-client
    VAULT=/path/to/prod.vault
    KEYPASS=123456  # or what you have set

    $SMS_CLIENT aliases $VAULT $KEYPASS | while read ALIAS; do
        $SMS_CLIENT pubkey $VAULT $KEYPASS $ALIAS >lib/keys/$ALIAS.pub
    done

Then, create the `lib/defaults` file:

    cd lib
    cp defaults.sh.example default.sh
    # edit defaults.sh

For using the tools, see their `-h/--help` instructions:

```
$ ./send-sms -h

Usage: send-sms [-dvha] [-b BROKER ] [-t TOPIC] [-p PARTITION] [-k KEY] [-g GROUP] [-o OFFSET] [[[[[[[PUBFILE] CLIENT] CORREL] DEADLINE] SENDER] RECIPIENT] FILE]

  Send an SMS over Kafka to the SMS Gateway

  POSITIONAL PARAMS
   PUBFILE    Public key file to encrypt with [lib/$CLIENT.pub]
   CLIENT     Client on whose behalf you are sending [test]
   CORREL     Unique correlation ID within the CLIENT [C2438511166]
   DEADLINE   Expiry of the SMS in ISO UTC [2100-01-01T00:00:00Z]
   SENDER     Sender name or number [SENDER]
   RECIPIENT  Destination number [255123456789]
   FILE       File to be sent or '-' for stdin

  COMMON OPTIONS
   -b,--broker=BROKER  Set the broker host:port [localhost:9092]
   -t,--topic=TOPIC    Set the topic [send-sms]
   -p,--partition=P    Set the partition []
   -k,--key=KEY        Set the event key []
   -g,--group=GID      Group ID of client []
   -o,--offset=OFFSET  Start reading at offset []
   -a,--all            Read the topic from the beginning
   -d,--dump           Write input to stderr / full output
   -v,--verbose        Display diagnostic output
   -h,--help           Display usage information

  All CAPITALISED options can also be passed as environment vars

```
