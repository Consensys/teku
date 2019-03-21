# artemis

 [![Build Status](https://jenkins.pegasys.tech/job/Artemis/job/master/badge/icon)](https://jenkins.pegasys.tech/job/Artemis/job/master/)
 [![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://github.com/PegasysEng/artemis/blob/master/LICENSE)
 [![Gitter chat](https://badges.gitter.im/PegaSysEng/artemis.png)](https://gitter.im/PegaSysEng/artemis)

Implementation of the Ethereum 2.0 Beacon Chain.

Based on the (evolving) [specification](https://github.com/ethereum/eth2.0-specs/blob/master/specs/core/0_beacon-chain.md).

> NOTE:  This repo is still in early development.

## Build Instructions

To build, clone this repo and run with `gradle` like so:

```
$ git clone --recursive https://github.com/PegaSysEng/artemis.git
$ cd artemis
$ ./gradlew
```

After a successful build, distribution packages will be available in `build/distributions`.

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
The integration tests can be triggered with:
```
$ ./gradlew integrationTest
```

## Run Options

To view the run menu:

```
$ ./gradlew run --args='-h'

Usage: Artemis [-hpV] [-l=<LOG VERBOSITY LEVEL>]
  -h, --help      Show this help message and exit.
  -l, --logging=<LOG VERBOSITY LEVEL> Logging verbosity levels: OFF, FATAL, WARN, INFO, DEBUG, TRACE, ALL (default: INFO).
  -p, --PoWChainServiceDisabled If this option is enabled then the PoW Chain service is disabled.
  -V, --version  Print version information and exit.
```

You can run the executable from the CLI with this command:
```
$ ./gradlew run
```

To run and send formatted output to a json file

```
$ ./gradlew run --args='-p=JSON -o=artemis.json'
```

>NOTE: If no -p isn't provided then it defaults to JSON

To run and send formatted output to a csv file

```
$ ./gradlew run --args='-p=CSV -o=artemis.csv'
```

To run with loggin level set to ALL

```
$ ./gradlew run --args='-l=ALL'
```

To run and generate flow diagrams for Artemis
```
$ ./gradlew run -PgenerateFlow
```
Note: You must be running [flow](http://findtheflow.io/)
