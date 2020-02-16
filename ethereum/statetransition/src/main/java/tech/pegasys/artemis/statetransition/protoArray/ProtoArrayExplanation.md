### **Understanding ProtoArray**

#### Variables needed:
- mapping of block root to array index 
- array where each elements (representing a block) contains:
    - best child (the child with the greatest weight)
    - best descendant (the descendant (i.e. latest child) with the greatest weight)
    - weight (the effective total balance of the validators that have voted (using their latest attestation) for this block or one of its descendants)
    - root of the block
- mapping of validator index to array (block) index which represent the block a validator had most recently voted on
- mapping of validator index to validator effective balance (sourced from justified checkpoint state)

#### `On block`:
- append one element to array where element has:
    - best descendant set to itself
    - best child is null
    - zero weight
- take the index and the root of the new block and add it to the mapping of block roots to array indices
- if block’s parent has a best child already, return
- if block’s parent does not have a child, go to the block’s parent and set its best child to the new block (and best descendant)

#### `On attestation`:
- create a new “diff” array that represents the weight changes that will be applied to the array from finalized index to the array.size()
- get the list of participants:
    - for each participant:
        - find the latest block vote and find validator effective balance
        - note in the diff array that you’re going to subtract the validator effective balance of this participant
        - Update the latest block vote of the validator
- calculate the weight of the attestation, i.e. get the sum of the effective balances of the validators that have participated in this attestation
- in the diff array find the block that the attestation is voting on, and note that you’re going to add the attestation weight to this block
- call `ApplyDiffArray()` function with the diffArray

#### `ApplyDiffArray(DiffArray)`:
- iterate from the head of the array down until the latest finalized block:
    - whenever you see a weight change noted on the diff array, apply that change to the weight of the element in the proto-array
    - check if the best child of the parent is a different block:
        - if yes, compare the new weight of the current block with the best child:
            - if the current block has higher weight:
                - update the parent block best child to the current block
                - update the parent block best descendant to the current block best descendant

#### `On justification`:
- create a new “diff” array that represents the weight changes that will be applied to the array from finalized index to the array.size()
- for every validator in the new justified checkpoint state:
    - check if the validator has a different effective balance from the effective balance of the found in the mapping:
        - if yes:
            - calculate the delta
            - find the most recent block the validator voted on
            - note down on the diff array to apply the delta to the block weight
- call `ApplyDiffArray()` with the diffArray
- update mapping of validator index to validator effective balance with the effective balances from the new justified state

