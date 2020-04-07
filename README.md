# teku

 [![Build Status](https://circleci.com/gh/PegaSysEng/teku.svg?style=svg)](https://circleci.com/gh/PegaSysEng/workflows/teku)
 [![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://github.com/PegasysEng/teku/blob/master/LICENSE)
 [![Gitter chat](https://badges.gitter.im/PegaSysEng/teku.png)](https://gitter.im/PegaSysEng/teku)

Implementation of the Ethereum 2.0 Beacon Chain.

Based on the (evolving) [specification](https://github.com/ethereum/eth2.0-specs/blob/dev/specs/phase0/beacon-chain.md).

## Build Instructions

### Install Prerequisites

**1) Java 11**

Ubuntu: `sudo apt install openjdk-11-jdk`

MacOS: `brew tap AdoptOpenJDK/openjdk && brew cask install adoptopenjdk11`

Other systems: [https://adoptopenjdk.net/] is very helpful. 

**2) Gradle**

Ubuntu: 

1) Download Gradle
```shell script
wget https://services.gradle.org/distributions/gradle-5.0-bin.zip -P /tmp
sudo unzip -d /opt/gradle /tmp/gradle-*.zip
```
2) Setup environment variables

Create gradle.sh in /etc/profile.d/

```shell script
export GRADLE_HOME=/opt/gradle/gradle-5.0
export PATH=${GRADLE_HOME}/bin:${PATH}
```

Make gradle.sh executable and source the script

```shell script
sudo chmod +x /etc/profile.d/gradle.sh
source /etc/profile.d/gradle.sh
```

OSX: `brew install gradle`

### Build and Dist

To create a ready to run distribution:

```shell script
git clone https://github.com/PegaSysEng/teku.git
cd teku && ./gradlew distTar installDist
```

This will produce:
- a fully packaged distribution in `build/distributions` 
- an expanded distribution, ready to run in `build/install/teku`

### Build and Test

To build, clone this repo and run with `gradle` like so:

```shell script
git clone https://github.com/PegaSysEng/teku.git
cd teku && ./gradlew

```

Or clone it manually:

```shell script
git clone https://github.com/PegaSysEng/teku.git
cd teku && ./gradlew
```

After a successful build, distribution packages will be available in `build/distributions`.

### Other Useful Gradle Targets

| Target       |  Description                              |
|--------------|--------------------------------------------
| distTar      | Builds a full distribution in build/distributions (as .tar.gz)
| distZip      | Builds a full distribution in build/distributions (as .zip)
| installDist  | Builds an expanded distribution in build/install/teku
| distDocker   | Builds the pegasyseng/teku docker image

## Run Multiple Teku nodes

### Prereqs:

- tmux

After building with `./gradlew distTar`, the simplest way to run is by using this command: 

```shell script
cd scripts && sh run.sh -n=[NUMBER OF NODES]
```

Help is available for this script as well:

```
sh run.sh -h
Runs a simulation of Teku with NODES nodes, where NODES > 0 and NODES < 256
Usage: sh run.sh [--numNodes, -n=NODES]  [--config=/path/to/your-config.yaml] [--logging, -l=OFF|FATAL|WARN|INFO|DEBUG|TRACE|ALL]
                 [--help, -h]
- If config files are specifed for specific nodes, those input files will be used to
configure their respective nodes.
- If no logging option is specified, then INFO is the default
```

## Run in Interop Mode

An interop script is provided to create a network with Teku and a number of other clients. 

### Prereqs:

- tmux
- [zcli](https://github.com/protolambda/zcli)
- You will need to have at least one other client built on your machine for this to work

The simplest way to run in interop mode is by using this command: 

```shell script
cd scripts
sh interop.sh [validator_count] [owned_validator_start_index] [owned_validator_count] [start_delay]
```
Help is available for this script as well:

```
sh interop.sh 
Runs a multiclient testnet
Usage: sh interop.sh [validator_count] [owned_validator_start_index] [owned_validator_count] [start_delay]
Example: Run multiple clients in interop mode using static peering. 16 validators and all are assigned to Teku
         sh interop.sh 16 0 16 10
```

### Manual configuration

To configure it manually, set these options in the config.toml:

```toml
[interop]
genesisTime = 5778872 #seconds since 1970-01-01 00:00:00 UTC
ownedValidatorStartIndex = 0
ownedValidatorCount = 8
startState = "/tmp/genesis.ssz"
privateKey = 0x00 #libp2p private key associated with this node's peerID

[deposit]

numValidators = 16

```

## Deposit Simulation

Click [here](pow/README.md) for setup instructions

## Code Style

We use Google's Java coding conventions for the project. To reformat code, run: 

```shell script 
./gradlew spotlessApply
```

Code style will be checked automatically during a build.

## Testing

All the unit tests are run as part of the build, but can be explicitly triggered with:

```shell script 
./gradlew test
```

## Run Options

To view the run menu:

```
./gradlew installDist
./build/install/teku/bin/teku -h

teku [OPTIONS] [COMMAND]

Description:

Run the Teku beacon chain client and validator

Options:
  -c, --config-file=<FILENAME>
                             Path/filename of the config file
      --data-path=<FILENAME> Path to output data files
      --data-storage-mode=<STORAGE_MODE>
                             Sets the strategy for handling historical chain
                               data.  Supported values include: 'prune', and
                               'archive'
      --eth1-deposit-contract-address=<ADDRESS>
                             Contract address for the deposit contract
      --eth1-endpoint=<NETWORK>
                             URL for Eth 1.0 node
  -h, --help                 Show this help message and exit.
  -l, --logging=<LOG VERBOSITY LEVEL>
                             Logging verbosity levels: OFF, FATAL, WARN, INFO,
                               DEBUG, TRACE, ALL (default: INFO).
      --log-color-enabled=<BOOLEAN>
                             Whether Status and Event log messages include a
                               console color display code
      --log-destination=<LOG_DESTINATION>
                             Whether a logger is added for the console, the log
                               file, or both
      --log-file=<FILENAME>  Path containing the location (relative or
                               absolute) and the log filename.
      --log-file-name-pattern=<REGEX>
                             Pattern for the filename to apply to rolled over
                               logs files.
      --log-include-events-enabled=<BOOLEAN>
                             Whether the frequent update events are logged (e.
                               g. every slot event, with validators and
                               attestations))
      --metrics-categories[=<METRICS_CATEGORY>[,<METRICS_CATEGORY>...]...]
                             Metric categories to enable
      --metrics-enabled=<BOOLEAN>
                             Enables metrics collection via Prometheus
      --metrics-interface=<NETWORK>
                             Metrics network interface to expose metrics for
                               Prometheus
      --metrics-port=<INTEGER>
                             Metrics port to expose metrics for Prometheus
  -n, --network=<NETWORK>    Represents which network to use
      --p2p-advertised-ip=<NETWORK>
                             Peer to peer advertised ip
      --p2p-advertised-port=<INTEGER>
                             Peer to peer advertised port
      --p2p-discovery-bootnodes[=<enode://id@host:port>[,<enode://id@host:
        port>...]...]
                             ENR of the bootnode
      --p2p-discovery-enabled=<BOOLEAN>
                             Enables discv5 discovery
      --p2p-enabled=<BOOLEAN>
                             Enables peer to peer
      --p2p-interface=<NETWORK>
                             Peer to peer network interface
      --p2p-peer-lower-bound=<INTEGER>
                             Lower bound on the target number of peers
      --p2p-peer-upper-bound=<INTEGER>
                             Upper bound on the target number of peers
      --p2p-port=<INTEGER>   Peer to peer port
      --p2p-private-key-file=<FILENAME>
                             This node's private key file
      --p2p-static-peers[=<PEER_ADDRESSES>[,<PEER_ADDRESSES>...]...]
                             Static peers
      --rest-api-docs-enabled=<BOOLEAN>
                             Enable swagger-docs and swagger-ui endpoints
      --rest-api-enabled=<BOOLEAN>
                             Enables Beacon Rest API
      --rest-api-interface=<NETWORK>
                             Interface of Beacon Rest API
      --rest-api-port=<INTEGER>
                             Port number of Beacon Rest API
  -V, --version              Print version information and exit.
      --validators-external-signer-public-keys[=<STRINGS>[,<STRINGS>...]...]
                             The list of external signer public keys
      --validators-external-signer-timeout=<INTEGER>
                             Timeout for the external signing service
      --validators-external-signer-url=<NETWORK>
                             URL for the external signing service
      --validators-key-file=<FILENAME>
                             The file to load validator keys from
      --validators-key-files[=<FILENAMES>[,<FILENAMES>...]...]
                             The list of encrypted keystore files to load the
                               validator keys from
      --validators-key-password-files[=<FILENAMES>[,<FILENAMES>...]...]
                             The list of password files to decrypt the
                               validator keystore files
Commands:
  transition  Manually run state transitions
  peer        Commands for LibP2P PeerID
  validator   Register validators by sending deposit transactions to an
                Ethereum 1 node
  genesis     Commands for generating genesis state

Teku is licensed under the Apache License 2.0

```

You can run the executable from the CLI with this command:
```shell script
./gradlew installDist
./build/install/teku/bin/teku
```

Refer to `config/config.yaml` for a set of default configuration settings.


## Special thanks
YourKit for providing us with a free profiler open source license. 

YourKit supports open source projects with innovative and intelligent tools
for monitoring and profiling Java and .NET applications.
YourKit is the creator of <a href="https://www.yourkit.com/java/profiler/">YourKit Java Profiler</a>,
<a href="https://www.yourkit.com/.net/profiler/">YourKit .NET Profiler</a>,
and <a href="https://www.yourkit.com/youmonitor/">YourKit YouMonitor</a>.

![YourKit Logo](https://www.yourkit.com/images/yklogo.png)
