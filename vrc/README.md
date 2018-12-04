# VRC Environmental Setup

Ensure you have Python3 installed before proceeding `python --version`

## Install Vyper Environment

`sudo apt install virtualenv`

`virtualenv -p python3.6 --no-site-packages ~/vyper-venv`

`source ~/vyper-venv/bin/activate`

Install this specific version as it is needed to compile the VRC contract

`pip install vyper==0.1.0b4`

## Install NPM Dependancies
`npm i -g truffle`

Truper is a utility to compile Vyper contracts for Truffle

`npm i -g truper`

`cd vrc`

`npm install`

## Compile and Test
`truffle compile`

`truper`

`truffle deploy`

`truffle test`
