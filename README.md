# artemis

 [![Build Status](https://jenkins.pegasys.tech/job/Artemis/job/master/badge/icon)](https://jenkins.pegasys.tech/job/Artemis/job/master/)
 [![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://github.com/PegasysEng/artemis/blob/master/LICENSE)
 [![Gitter chat](https://badges.gitter.im/PegaSysEng/artemis.png)](https://gitter.im/PegaSysEng/artemis)

Implementation of the Ethereum 2.0 Beacon Chain.

Based on the (evolving) [specification](https://github.com/ethereum/eth2.0-specs/blob/master/specs/core/0_beacon-chain.md).

## Interop Intructions
### Prerequisites
You need to have Java 11 installed.

Ubuntu: `sudo apt install openjdk-11-jdk`
MacOS: `brew tap AdoptOpenJDK/openjdk && brew cask install adoptopenjdk11`
Other systems: [https://adoptopenjdk.net/] is very helpful. 

### Building
To setup for testing interop with other clients:

```shell script
git clone --recursive https://github.com/PegaSysEng/artemis.git
cd artemis
./gradlew distTar installDist
```

This will produce a fully packaged distribution in `build/distributions` and an expanded 
distribution, ready to run in `build/install/artemis`.

Alternatively you can build a docker image with `./gradlew distDocker`.

### Configuration

Artemis' configuration comes from a TOML configuration file, specified using the `--config` argument.
`config/config.toml` provides a useful starting point.  Values that you will likely want to set:

```toml
[node]
networkMode = "jvmlibp2p"
identity = "0x01" # Some unique identity for the node
discovery = "static"
isBootnode = false
peers = ["<peer>"]
networkInterface = "127.0.0.1"
port = 90001 # Some unique port

[interop]
active = true
ownedValidatorStartIndex = 0
ownedValidatorCount = 4
startState = "/tmp/genesis.ssz"  # Genesis file to load
```

## Build Instructions

### Full build and Test
To build, clone this repo and run with `gradle` like so:

```shell script
$ git clone --recursive https://github.com/PegaSysEng/artemis.git
$ cd artemis
$ ./gradlew
```

After a successful build, distribution packages will be available in `build/distributions`.

### Other Useful Gradle Targets

| Target       |  Description                              |
|--------------|--------------------------------------------
| distTar      | Builds a full distribution in build/distributions (as .tar.gz)
| distZip      | Builds a full distribution in build/distributions (as .zip)
| installDist  | Builds an expanded distribution in build/install/artemis
| distDocker   | Builds the pegasyseng/artemis docker image

## Run Multiple Artemis nodes

#### Prereqs:

- tmux

After building with `./gradlew distTar`, the simplest way to run is by using this command: 

```bash
$ cd scripts
$ sh run.sh -n=[NUMBER OF NODES]
```

Help is available for this script as well:

```bash
$ sh run.sh -h
Runs a simulation of artemis with NODES nodes, where NODES > 0 and NODES < 256
Usage: sh run.sh [--numNodes, -n=NODES]  [--config=/path/to/your-config.toml] [--logging, -l=OFF|FATAL|WARN|INFO|DEBUG|TRACE|ALL]
                 [--help, -h]
- If config files are specifed for specific nodes, those input files will be used to
configure their respective nodes.
- If no logging option is specified, then INFO is the default
```

## Run in Interop Mode

An interop script is provided to create a network with Artemis and a number of other clients. 

### Prereqs:

- tmux
- [zcli](https://github.com/protolambda/zcli)
- You will need to have at least one other client built on your machine for this to work

The simplest way to run in interop mode is by using this command: 

```bash
$ cd scripts
$ sh interop.sh [validator_count] [owned_validator_start_index] [owned_validator_count] [start_delay]
```
Help is available for this script as well:

```bash
$ sh interop.sh 
Runs a multiclient testnet
Usage: sh interop.sh [validator_count] [owned_validator_start_index] [owned_validator_count] [start_delay]
Example: Run multiple clients in interop mode using static peering. 16 validators and all are assigned to Artemis
         sh interop.sh 16 0 16 10
```

### Manual configuration

To configure it manually, set these options in the config.toml:

```bash
[interop]
active = true
genesisTime = [seconds since 1970-01-01 00:00:00 UTC]
ownedValidatorStartIndex = [int]
ownedValidatorCount = [int]
startState = "/tmp/genesis.ssz"
privateKey = [libp2p private key associated with this node's peerID]

[deposit]

numValidators = 16

```

## Code Style

We use Google's Java coding conventions for the project. To reformat code, run: 

```
$ ./gradlew spotlessApply
```

Code style will be checked automatically during a build.

## Testing

All the unit tests are run as part of the build, but can be explicitly triggered with:
```
$ ./gradlew test
```

## Run Options

To view the run menu:

```
$ ./gradlew run --args='-h'

artemis [OPTIONS] [COMMAND]

Description:

Run the Artemis beacon chain client and validator

Options:
  -c, --config=<FILENAME>   Path/filename of the config file
  -h, --help                Show this help message and exit.
  -l, --logging=<LOG VERBOSITY LEVEL>
                            Logging verbosity levels: OFF, FATAL, WARN, INFO,
                              DEBUG, TRACE, ALL (default: INFO).
  -V, --version             Print version information and exit.
Commands:
  transition  Manually run state transitions
  peer        Commands for LibP2P PeerID

Artemis is licensed under the Apache License 2.0

```

You can run the executable from the CLI with this command:
```
$ ./gradlew run
```

Refer to `config/config.toml` for a set of default configuration settings.


To run with logging level set to DEBUG

```
$ ./gradlew run --args='-l=DEBUG'
```

To profile and/or generate flow diagrams for Artemis: 

Setup:

```bash
$ source artemis.env 
```

Run:


Terminal 1:

```bash
$ flow
```

Terminal 2:
``` bash
$ ./gradlew run -PgenerateFlow
```


