￿## Proof of Work Chain Environmental Setup

1. `cd` to the artemis root directory
2. `git submodule update --init --recursive`
3. `cd ganache-cli`
4. `npm install`

> Note: You will need python 2.7 for npm install to complete successfully

## Auto-generate DepositContract class from Vyper contract

> Note: Disregard these steps if you just want to run the simulation.

Install web3j.  On MacOS this can be done with `brew`:
```shell script
brew tap web3j/web3j
brew install web3j
```

Ensure you have Python3 installed before proceeding

`python --version`

Setup Vyper Virtual Environment
```
sudo apt install virtualenv
virtualenv -p python3.6 --no-site-packages ~/vyper-venv
source ~/vyper-venv/bin/activate
```

Install this specific version as it is needed to compile the deposit_contract contract

`pip install vyper==0.1.0b12`

Generate the ABI

`vyper -f abi validator_registration.v.py > DepositContract.abi`

Generate the binary

`vyper -f bytecode validator_registration.v.py > DepositContract.bin`

**Warning: Next step will overwrite the current version of DepositContract.java**

Auto-generate the DepositContract class

`web3j solidity generate -b DepositContract.bin -a DepositContract.abi -o [PATH_TO_ARTEMIS_ROOT]/pow/src/main/java -p tech.pegasys.artemis.pow.contract`
