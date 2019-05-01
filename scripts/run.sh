#!/bin/sh

DIR=$(dirname $0)
NODES=$1

source $DIR/utils.sh

if [[ "$#" -ne 1 || "$NODES" -lt 1 ]]
then 
  usage 
  exit
fi

# Clean the demo directory
clean demo

# Create a list of all the peers for the configure node procedure to use
COMBINATIONS=$(seq 19000 $((19000 + $NODES - 1)))
PEERS=$(echo "$COMBINATIONS" | sed -E "s/^([0-9]+)/\"hob+tcp:\/\/abcf@localhost:\1\"/g")

# Loop over all of the nodes to be created and configure them
i=0
while [ $i -lt $NODES ] 
do
  configure_node $i $NODES
  i=$(($i + 1))
done

cd demo/

tmux new-session -d -s foo 'cd node_0 && ./artemis --config=./config/runConfig.0.toml --logging=INFO'

# TODO
create_tmux_panes 

tmux select-layout tiled
tmux rename-window 'the dude abides'
tmux attach-session -d
