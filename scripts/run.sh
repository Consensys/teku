#!/bin/bash

# **** Usage **** #

usage() {
  echo "Runs a simulation of teku with NODES number of nodes, where NODES > 0 and NODES < 256 and VALIDATORS number of validators divided equally among nodes"
  echo "Usage: sh run.sh [--numNodes, -n=NODES]  [--config=/path/to/your-config.toml] [--logging, -l=OFF|FATAL|WARN|INFO|DEBUG|TRACE|ALL]"
  echo "                 [--help, -h] [--numValidators, -v=VALIDATORS]"
  echo "Note: "
  echo "- If config files are specifed for specific nodes, those input files will be used to"
  echo "configure their respective nodes."
  echo "- If no logging option is specified, then INFO is the default"
}

# **** Script **** #

# Get the working directory of this script file
DIR=$(dirname $0)

# Create the arrays to hold the input files that were specified 
INPUTS=()
NODES=4

# Source the functions from the utilities script
source $DIR/run_utils.sh

# Parse the inputs to the script
for arg in "$@"
do 
  shift
  case "$arg" in

    # Match the -n or --numNodes option and set NODES to the provided argument
    -n=*|--numNodes=*)
      NODES="${arg#*=}" ;;

    # Match the -v or --numValidators option and set VALIDATORS to the provided argument
    -v=*|--numValidators=*)
      VALIDATORS="${arg#*=}" ;;

    # Match the -m or --networkMode option and set MODE to the provided argument
    -m=*|--NetworkMode=*)
      MODE="${arg#*=}" ;;

    # Match the -i or --inputFile option and update the INPUTS array with the output file path
    "--config"*)
      FILE="${arg#*=}"
      IDX=$(echo $FILE | sed -E "s/.*[a-zA-Z0-9]+\.([0-9]+)\.toml/\1/")
      INPUTS[$IDX]="$FILE" ;;

    # Match the -l or --logging option and set LOG mode to the provided argument
    -l=*|--logging=*)
      LOG="${arg#*=}" ;;

    # Print the usage and exit if the help flag is provided
    -h|--help) usage; exit 0 ;;

	-v|--vertical)
		VERTICAL=true ;;

    # Pipe the usage to stderr and exit on exitcode 1 if an incorrect flag is provided
    --*) usage >&2; exit 1 ;;
  esac
done

# If NODES is not an integer or is an invalid number, pipe the usage statement to stderr and exit
# with exit code 3
if [ "$(echo "$NODES" | sed "s/[0-9]//g")" != "" ] || [[ $NODES -lt 1 || $NODES -gt 255 ]] 
then 
  usage >&2; exit 3
fi

# If MODE is undefined default to jvmlibp2p
[[ -z "$MODE" ]] && MODE="jvmlibp2p"

# If MODE is not a valid input pipe the usage statement to stderr and exit
# with exit code 3
if [ "$MODE" != "jvmlibp2p" ]
then
  usage >&2; exit 3
fi

# Set logging cli flag if it was supplied
LOG_FLAG=""
[[ -z "$LOG" ]] || LOG_FLAG="--logging=$LOG"

# If VALIDATORS is undefined default to INFO
[[ -z "$VALIDATORS" ]] && VALIDATORS=64


LOG_OPTIONS=("OFF FATAL WARN INFO DEBUG TRACE ALL")
# If LOG is not a valid input pipe the usage statement to stderr and exit
# with exit code 3
if [[ -n "$LOG" && ! " ${LOG_OPTIONS[@]} " =~ " ${LOG} " ]]
then
  usage >&2; exit 3
fi


# Clean the demo directory
rm -rf ./demo
mkdir -p ./demo

# Clean out the old configuration files
rm -f ../config/runConfig.*
rm -f ../config/*.dat

START_DELAY=20
CURRENT_TIME=$(date +%s)
GENESIS_TIME=$((CURRENT_TIME + START_DELAY))

# Create the binaries, configuration files, and symlinks for each node
i=0
while [ $i -lt $NODES ] 
do
  configure_node $MODE $i $NODES $VALIDATORS ${INPUTS[$i]}
  i=$(($i + 1))
done

# Create a properly formatted tmux session for the simulation
create_tmux_windows $NODES $VERTICAL
