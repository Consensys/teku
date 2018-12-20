pragma solidity ^0.5.0;

contract VRC {

    uint256 MIN_DEPOSIT = 1; //ETH
    uint256 MAX_DEPOSIT = 32; //ETH
    uint256 GWEI_PER_ETH = 1000000000;
    uint256 CHAIN_START_FULL_DEPOSIT_THRESHOLD = 16384;
    uint32 DEPOSIT_CONTRACT_TREE_DEPTH = 32;
    uint256 SECONDS_PER_DAY = 86400;

    uint256 WEI_PER_ETH = 10**18;
    //2**14
    uint256 DEPOSITS_FOR_CHAIN_START = 2**14;
    uint256 DEPOSIT_SIZE = 32 * WEI_PER_ETH;
    uint256 MIN_TOPUP_SIZE = 1 * WEI_PER_ETH;
    //10**9

    uint256 POW_CONTRACT_MERKLE_TREE_DEPTH = 32;
    uint256 SECONDS_PER_DAY = 86400;

    event Eth1Deposit(bytes32 previous_receipt_root, bytes[2064] data, uint256 deposit_count);
    event ChainStart(bytes32 receipt_root, bytes[8] time);

    bytes32[uint256] receipt_tree;
    uint256 deposit_count;
    uint256 full_deposit_count;

    function deposit(bytes[2048] deposit_parameters) public payable {

    }

    function get_receipt_root() public view returns (bytes32){
        return receipt_tree[1];
    }
}


/*
## compiled with v0.1.0-beta.4 ##

MIN_DEPOSIT: constant(uint256) = 1  # ETH
MAX_DEPOSIT: constant(uint256) = 32  # ETH
GWEI_PER_ETH: constant(uint256) = 1000000000  # 10**9
CHAIN_START_FULL_DEPOSIT_THRESHOLD: constant(uint256) = 16384  # 2**14
DEPOSIT_CONTRACT_TREE_DEPTH: constant(uint256) = 32
SECONDS_PER_DAY: constant(uint256) = 86400

Eth1Deposit: event({previous_receipt_root: bytes32, data: bytes[2064], deposit_count: uint256})
ChainStart: event({receipt_root: bytes32, time: bytes[8]})

receipt_tree: bytes32[uint256]
deposit_count: uint256
full_deposit_count: uint256

@payable
@public
def deposit(deposit_parameters: bytes[2048]):
assert msg.value >= as_wei_value(MIN_DEPOSIT, "ether")
assert msg.value <= as_wei_value(MAX_DEPOSIT, "ether")

index: uint256 = self.deposit_count + 2**DEPOSIT_CONTRACT_TREE_DEPTH
msg_gwei_bytes8: bytes[8] = slice(concat("", convert(msg.value / GWEI_PER_ETH, bytes32)), start=24, len=8)
timestamp_bytes8: bytes[8] = slice(concat("", convert(block.timestamp, bytes32)), start=24, len=8)
deposit_data: bytes[2064] = concat(msg_gwei_bytes8, timestamp_bytes8, deposit_parameters)

log.Eth1Deposit(self.receipt_tree[1], deposit_data, self.deposit_count)

# add deposit to merkle tree
self.receipt_tree[index] = sha3(deposit_data)
for i in range(32):  # DEPOSIT_CONTRACT_TREE_DEPTH (range of constant var not yet supported)
index /= 2
self.receipt_tree[index] = sha3(concat(self.receipt_tree[index * 2], self.receipt_tree[index * 2 + 1]))

self.deposit_count += 1
if msg.value == as_wei_value(MAX_DEPOSIT, "ether"):
self.full_deposit_count += 1
if self.full_deposit_count == CHAIN_START_FULL_DEPOSIT_THRESHOLD:
timestamp_day_boundary: uint256 = as_unitless_number(block.timestamp) - as_unitless_number(block.timestamp) % SECONDS_PER_DAY + SECONDS_PER_DAY
timestamp_day_boundary_bytes8: bytes[8] = slice(concat("", convert(timestamp_day_boundary, bytes32)), start=24, len=8)
log.ChainStart(self.receipt_tree[1], timestamp_day_boundary_bytes8)

@public
@constant
def get_receipt_root() -> bytes32:
return self.receipt_tree[1]*/