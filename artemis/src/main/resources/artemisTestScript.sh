#!/bin/bash

./gradlew run > artemisTestOutput.txt &
artemisPid=$!

slotCounter=0

tail -fn0 ./artemisTestOutput.txt | \
while read line ; do
	echo "$line"


	# Terminate After 16 Slots/2 Epochs
	if [ $slotCounter -gt 16 ]
	then
		break
	fi
        if [[ $line =~ .*Slot\ Event.* ]]
        then
		((slotCounter++))
        fi


	# Handle Exceptions
	# Fail on Exception
	if [[ $line =~ .*Exception.* ]]
	then
		# Error
		exit 1
	fi
	# Fail on Mismatch State Root
	if [[ $line =~ .*Block\ state\ root\ does\ NOT\ match\ the\ calculated\ state\ root.* ]]
	then
		# Error
		exit 2
	fi
	# Fail on Unable to Update Justified and Finalized Roots
	if [[ $line =~ .*Can\'t\ update\ justified\ and\ finalized\ block\ roots.* ]]
	then
		# Error
		exit 3
	fi
	# Fail on Unable to Update Block Using LMDGhost
	if [[ $line =~ .*Can\'t\ update\ head\ block\ using\ LMDGhost.* ]]
	then
		# Error
		exit 4
	fi
done
kill $artemisPid
rm ./artemisTestOutput.txt
exit 0

