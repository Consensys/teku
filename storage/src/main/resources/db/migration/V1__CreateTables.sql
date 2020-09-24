-- Chain Storage
CREATE TABLE block (
  blockRoot BINARY(32) NOT NULL PRIMARY KEY,
  slot DECIMAL(20, 0) NOT NULL,
  parentRoot BINARY(32) NOT NULL,
  finalized BOOLEAN NOT NULL,
  ssz LONGBLOB
);
CREATE INDEX idxBlockSlot ON block(slot);

CREATE TABLE state (
  stateRoot BINARY(32) NOT NULL PRIMARY KEY,
  blockRoot BINARY(32) NOT NULL,
  slot DECIMAL(20, 0) NOT NULL,
  ssz LONGBLOB
);
CREATE INDEX idxStateBlockRootSlot ON state(blockRoot, slot);
CREATE INDEX idxStateSlot ON state(slot);

CREATE TABLE checkpoint (
  type VARCHAR(20) NOT NULL PRIMARY KEY,
  blockRoot BINARY(32) NOT NULL,
  epoch DECIMAL(20, 0) NOT NULL
) WITHOUT ROWID;

CREATE TABLE vote (
  validatorIndex DECIMAL(20, 0) NOT NULL PRIMARY KEY,
  currentRoot BINARY(32) NOT NULL,
  nextRoot BINARY(32) NOT NULL,
  nextEpoch DECIMAL(20, 0) NOT NULL
) WITHOUT ROWID;


-- ProtoArray

CREATE TABLE protoarray (
  id INT NOT NULL DEFAULT 1 PRIMARY KEY,
  justifiedEpoch DECIMAL(20, 0) NOT NULL,
  finalizedEpoch DECIMAL(20, 0) NOT NULL,
  blocks BINARY(100) NOT NULL
);

-- ETH1

CREATE TABLE eth1_min_genesis (
  id INT NOT NULL DEFAULT 1 PRIMARY KEY,
  block_timestamp DECIMAL(20, 0) NOT NULL,
  block_number DECIMAL(20, 0) NOT NULL,
  block_hash BINARY(32) NOT NULL
);

CREATE TABLE eth1_deposit_block (
  block_number DECIMAL(20, 0) NOT NULL PRIMARY KEY,
  block_timestamp DECIMAL(20, 0) NOT NULL,
  block_hash BINARY(32) NOT NULL
) WITHOUT ROWID;

CREATE TABLE eth1_deposit (
  merkle_tree_index DECIMAL(20, 0) NOT NULL PRIMARY KEY,
  block_number DECIMAL(20, 0) NOT NULL,
  public_key BINARY(48) NOT NULL,
  withdrawal_credentials BINARY(32) NOT NULL,
  signature BINARY(96) NOT NULL,
  amount DECIMAL(20, 0) NOT NULL
) WITHOUT ROWID;
CREATE INDEX idxDepositBlockNumber ON eth1_deposit(block_number);