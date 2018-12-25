# VRC Environmental Setup

Ensure you have Python3 installed before proceeding `python --version`

## Install Vyper Environment

```
sudo apt install virtualenv
virtualenv -p python3.6 --no-site-packages ~/vyper-venv
source ~/vyper-venv/bin/activate
```

Install this specific version as it is needed to compile the VRC contract

`pip install vyper==0.1.0b6`

## Install NPM Dependancies
`npm i -g truffle`

Truper is a utility to compile Vyper contracts for Truffle
```
npm i -g truper
cd pow
npm install
```

## Compile and Test
```
truffle compile
truper
truffle deploy
truffle test
```
## Running
Accessing the contracts from the Artemis Environment, edit the following attributes of `pow/config.properties`
```
provider = http://127.0.0.1:7545
mnemonic = retreat attack lift winter amazing noodle interest dutch craft old solve save
vrc_contract_address = 0x9E6fc1FbD8703bb414Ce2575090562821db75832
```
Until a more automated run process is in place, please edit these value to reflect your environment. Address of the mnemonic phrase will need an ether balance to deploy the contract to the Test RPC.