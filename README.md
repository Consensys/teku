# teku

 [![Build Status](https://circleci.com/gh/PegaSysEng/teku.svg?style=svg)](https://circleci.com/gh/PegaSysEng/workflows/teku)
 [![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://github.com/PegasysEng/teku/blob/master/LICENSE)
 [![Gitter chat](https://badges.gitter.im/PegaSysEng/teku.png)](https://gitter.im/PegaSysEng/teku)

Teku is a Java implementation of the Ethereum 2.0 Beacon Chain.

## Useful links 

* [Ethereum 2.0 Beacon Chain specification](https://github.com/ethereum/eth2.0-specs/blob/dev/specs/phase0/beacon-chain.md) 
* [Teku user documentation](https://docs.teku.pegasys.tech/en/latest/)
* [Teku REST API reference documentation](https://pegasyseng.github.io/teku/latest/)
* [Teku issues](https://github.com/PegaSysEng/teku/issues)
* [Contribution guidelines](CONTRIBUTING.md)

## Teku users 

See our [user documentation](https://docs.teku.pegasys.tech/en/latest/). 

Raise a [documentation issue](https://github.com/PegaSysEng/doc.teku/issues) or get in touch on 
[Gitter](https://gitter.im/PegaSysEng/teku) if you've got questions or feedback. 

## Teku developers 

* [Contribution Guidelines](CONTRIBUTING.md)
* [Coding Conventions](https://github.com/hyperledger/besu/blob/master/CODING-CONVENTIONS.md)

## Build Instructions

### Install Prerequisites

* Java 11

### Build and Dist

To create a ready to run distribution:

```shell script
git clone https://github.com/PegaSysEng/teku.git
cd teku && ./gradlew distTar installDist
```

This produces:
- Fully packaged distribution in `build/distributions` 
- Expanded distribution, ready to run in `build/install/teku`

### Build and Test

To build, clone this repo and run with `gradle`:

```shell script
git clone https://github.com/PegaSysEng/teku.git
cd teku && ./gradlew

```

Or clone it manually:

```shell script
git clone https://github.com/PegaSysEng/teku.git
cd teku && ./gradlew
```

After a successful build, distribution packages are available in `build/distributions`.

### Other Useful Gradle Targets

| Target       | Builds                              |
|--------------|--------------------------------------------
| distTar      | Full distribution in build/distributions (as `.tar.gz`)
| distZip      | Full distribution in build/distributions (as `.zip`)
| installDist  | Expanded distribution in `build/install/teku`
| distDocker   | The `pegasyseng/teku` docker image

## Code Style

We use Google's Java coding conventions for the project. To reformat code, run: 

```shell script 
./gradlew spotlessApply
```

Code style is checked automatically during a build.

## Testing

All the unit tests are run as part of the build, but can be explicitly triggered with:

```shell script 
./gradlew test
```

## Special thanks
YourKit for providing us with a free profiler open source license. 

YourKit supports open source projects with innovative and intelligent tools
for monitoring and profiling Java and .NET applications.
YourKit is the creator of <a href="https://www.yourkit.com/java/profiler/">YourKit Java Profiler</a>,
<a href="https://www.yourkit.com/.net/profiler/">YourKit .NET Profiler</a>,
and <a href="https://www.yourkit.com/youmonitor/">YourKit YouMonitor</a>.

![YourKit Logo](https://www.yourkit.com/images/yklogo.png)
