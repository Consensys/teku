#Proof of Work Chain Environmental Setup
1. `cd` to the artemis root directory
2. `git submodule update --init --recursive`
3. `cd ganache-cli`
4. `npm install`

# VRC Environmental Setup

Ensure you have Python3 installed before proceeding `python --version`

##Auto-generate DepositContract class from Vyper contract

```
sudo apt install virtualenv
virtualenv -p python3.6 --no-site-packages ~/vyper-venv
source ~/vyper-venv/bin/activate
```

Install this specific version as it is needed to compile the deposit_contract contract

`pip install vyper==0.1.0b9`

## Running
Accessing the contracts from the Artemis Environment, edit the following attributes of `pow/config.properties`
```
provider = http://127.0.0.1:7545
mnemonic = retreat attack lift winter amazing noodle interest dutch craft old solve save
vrc_contract_address = 0x9E6fc1FbD8703bb414Ce2575090562821db75832
```
Until a more automated run process is in place, please edit these value to reflect your environment. Address of the mnemonic phrase will need an ether balance to deploy the contract to the Test RPC.