#!/bin/sh

# Get the working directory of this script file
DIR=$(dirname $0)

# Get the shell parameter that was provided
NODES=$1

# Source the functions from the utilities script
source $DIR/utils.sh

# If the shell parameters are invalid, print the usage statement and exit
if [[ "$#" -ne 1 || "$NODES" -lt 1 || "$NODES" -gt 255 ]]
then 
  usage 
  exit
fi

# Clean the demo directory
clean demo

# Clean out the old configuration files
#clean_config

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

# Create a properly formatted tmux session for the simulation
create_tmux_windows $NODES
