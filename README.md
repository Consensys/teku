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

### Running

An interop script is provided to create a network with Artemis and a number of other clients. 
The script requires both tmux and [zcli](https://github.com/protolambda/zcli) to be installed and available on the path. Run with:
```shell script
cd scripts
bash interop.sh [validator_count] [owned_validator_start_index] [owned_validator_count] [start_delay]
```

Alternatively Artemis can be run stand-alone using the `bin/artemis` script from a distribution.
You can use `./build/install/artemis/bin/artemis` if you followed the build instructions above.

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

## Run Demo

After building with `./gradlew distTar`, follow these instructions:

```bash
$ cd scripts
$ sh run.sh -n=[NUMBER OF NODES]
```

> Note:  You will need tmux installed for this demo to work

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

Usage: Artemis [-hV] [-c=<FILENAME>] [-l=<LOG VERBOSITY LEVEL>]
-c, --config=<FILENAME>   Path/filename of the config file
-h, --help                Show this help message and exit.
-l, --logging=<LOG VERBOSITY LEVEL>
                          Logging verbosity levels: OFF, FATAL, WARN, INFO, DEBUG,
                            TRACE, ALL (default: INFO).
-V, --version             Print version information and exit.

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

## Interop Mode

To activate interop mode with a predefined genesis state, change the config.toml as follows:

```
[interop]
active = true
genesisTime = 1568830184
ownedValidatorStartIndex = 0
ownedValidatorCount = 16
startState = "/tmp/genesis.ssz"
privateKey = "0x08021221008166B8EF20C11F3A18F8774BF834173B07F64BAEDA981766896B4A8F53B52EDF"
```

> NOTE: Under `deposit` set `numValidators` to the agreed number of initial validators (`validator_count` from the spec)