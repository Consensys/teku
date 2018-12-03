MIN_DEPOSIT: constant(uint256) = 1  # ETH
MAX_DEPOSIT: constant(uint256) = 32  # ETH
GWEI_PER_ETH: constant(uint256) = 1000000000  # 10**9
CHAIN_START_FULL_DEPOSIT_THRESHOLD: constant(uint256) = 16384  # 2**14
POW_CONTRACT_MERKLE_TREE_DEPTH: constant(uint256) = 32
SECONDS_PER_DAY: constant(uint256) = 86400

HashChainValue: event({previous_receipt_root: bytes32, data: bytes[2064], full_deposit_count: uint256})
ChainStart: event({receipt_root: bytes32, time: bytes[8]})

receipt_tree: bytes32[uint256]
full_deposit_count: uint256

@payable
@public
def deposit(deposit_parameters: bytes[2048]):
    index: uint256 = self.full_deposit_count + 2**POW_CONTRACT_MERKLE_TREE_DEPTH
    msg_gwei_bytes8: bytes[8] = slice(concat("", convert(msg.value / GWEI_PER_ETH, bytes32)), start=24, len=8)
    timestamp_bytes8: bytes[8] = slice(concat("", convert(block.timestamp, bytes32)), start=24, len=8)
    deposit_data: bytes[2064] = concat(msg_gwei_bytes8, timestamp_bytes8, deposit_parameters)

    log.HashChainValue(self.receipt_tree[1], deposit_data, self.full_deposit_count)

    self.receipt_tree[index] = sha3(deposit_data)
    for i in range(32):  # POW_CONTRACT_MERKLE_TREE_DEPTH (range of constant var not yet supported)
        index /= 2
        self.receipt_tree[index] = sha3(concat(self.receipt_tree[index * 2], self.receipt_tree[index * 2 + 1]))

    assert msg.value >= as_wei_value(MIN_DEPOSIT, "ether")
    assert msg.value <= as_wei_value(MAX_DEPOSIT, "ether")
    if msg.value == as_wei_value(MAX_DEPOSIT, "ether"):
        self.full_deposit_count += 1
    if self.full_deposit_count == CHAIN_START_FULL_DEPOSIT_THRESHOLD:
        timestamp_day_boundary: uint256 = as_unitless_number(block.timestamp) - as_unitless_number(block.timestamp) % SECONDS_PER_DAY + SECONDS_PER_DAY
        timestamp_day_boundary_bytes8: bytes[8] = slice(concat("", convert(timestamp_day_boundary, bytes32)), start=24, len=8)
        log.ChainStart(self.receipt_tree[1], timestamp_day_boundary_bytes8)

@public
@constant
def get_receipt_root() -> bytes32:
    return self.receipt_tree[1]
