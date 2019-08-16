# artemis

 [![Build Status](https://jenkins.pegasys.tech/job/Artemis/job/master/badge/icon)](https://jenkins.pegasys.tech/job/Artemis/job/master/)
 [![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://github.com/PegasysEng/artemis/blob/master/LICENSE)
 [![Gitter chat](https://badges.gitter.im/PegaSysEng/artemis.png)](https://gitter.im/PegaSysEng/artemis)

Implementation of the Ethereum 2.0 Beacon Chain.

Based on the (evolving) [specification](https://github.com/ethereum/eth2.0-specs/blob/master/specs/core/0_beacon-chain.md).

## Build Instructions

To build, clone this repo and run with `gradle` like so:

```
$ git clone --recursive https://github.com/PegaSysEng/artemis.git
$ cd artemis
$ ./gradlew
```

After a successful build, distribution packages will be available in `build/distributions`.

## Run Demo (Hobbits)

After building, follow these instructions:

```bash
$ cd scripts
$ sh run.sh -n=[NUMBER OF NODES]
```

## Run Demo (Mothra)

After building, follow these instructions:

```bash
$ cd scripts
$ sh run.sh -n=[NUMBER OF NODES] -m=mothra
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

To run and send formatted output to a json file:
```
[output]
outputFile = "artemis.json"
providerType = "JSON"
```

Then run:
```
$ ./gradlew run
```

To run and send formatted output to a csv file:
```
[output]
outputFile = "artemis.csv"
providerType = "CSV"
```

Then run:
```
$ ./gradlew run
```

To run with loggin level set to DEBUG

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

## Activating Interop Mode

To initialize BeaconState and Validators staticly for interop testing, change the
interop settings in `config/config.toml`.

1) Set the active boolean to true.
2) Set the inputFile string to the JSON file that has Validator private key and Deposit objec information.

```
[interop]
active = true
inputFile = "interopDepositsAndKeys.json"
```
