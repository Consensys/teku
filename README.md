# artemis

 [![Build Status](https://circleci.com/gh/PegaSysEng/artemis.svg?style=svg)](https://circleci.com/gh/PegaSysEng/workflows/artemis)
 [![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://github.com/PegasysEng/artemis/blob/master/LICENSE)
 [![Gitter chat](https://badges.gitter.im/PegaSysEng/artemis.png)](https://gitter.im/PegaSysEng/artemis)

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
git clone https://github.com/PegaSysEng/artemis.git
cd artemis && ./gradlew distTar installDist
```

This will produce:
- a fully packaged distribution in `build/distributions` 
- an expanded distribution, ready to run in `build/install/artemis`

### Build and Test

To build, clone this repo and run with `gradle` like so:

```shell script
git clone https://github.com/PegaSysEng/artemis.git
cd artemis && ./gradlew

```

Or clone it manually:

```shell script
git clone https://github.com/PegaSysEng/artemis.git
cd artemis && ./gradlew
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

### Prereqs:

- tmux

After building with `./gradlew distTar`, the simplest way to run is by using this command: 

```shell script
cd scripts && sh run.sh -n=[NUMBER OF NODES]
```

Help is available for this script as well:

```
sh run.sh -h
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

```shell script
cd scripts
sh interop.sh [validator_count] [owned_validator_start_index] [owned_validator_count] [start_delay]
```
Help is available for this script as well:

```
sh interop.sh 
Runs a multiclient testnet
Usage: sh interop.sh [validator_count] [owned_validator_start_index] [owned_validator_count] [start_delay]
Example: Run multiple clients in interop mode using static peering. 16 validators and all are assigned to Artemis
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
  -c, --config=<FILENAME>   Path/filename of the config file
  -h, --help                Show this help message and exit.
  -l, --logging=<LOG VERBOSITY LEVEL>
                            Logging verbosity levels: OFF, FATAL, WARN, INFO,
                              DEBUG, TRACE, ALL (default: INFO).
  -V, --version             Print version information and exit.
Commands:
  transition  Manually run state transitions
  peer        Commands for LibP2P PeerID

Teku is licensed under the Apache License 2.0

```

You can run the executable from the CLI with this command:
```shell script
./gradlew installDist
./build/install/teku/bin/teku
```

Refer to `config/config.toml` for a set of default configuration settings.


## Special thanks
YourKit for providing us with a free profiler open source license. 

YourKit supports open source projects with innovative and intelligent tools
for monitoring and profiling Java and .NET applications.
YourKit is the creator of <a href="https://www.yourkit.com/java/profiler/">YourKit Java Profiler</a>,
<a href="https://www.yourkit.com/.net/profiler/">YourKit .NET Profiler</a>,
and <a href="https://www.yourkit.com/youmonitor/">YourKit YouMonitor</a>.

![YourKit Logo](https://www.yourkit.com/images/yklogo.png)
