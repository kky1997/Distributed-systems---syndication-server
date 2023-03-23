#!/bin/bash
rm replica.txt
java AggregationServer 4567 >> trash.txt &
sleep 2
java ContentServer AggregationServer:4567 sample.txt 1 >> trash.txt &
sleep 2
java GETClient AggregationServer:4567 >> output_1.txt
pkill -f 'ContentServer AggregationServer:4567 sample.txt 1'
sleep 12
java GETClient AggregationServer:4567 >> output_2.txt
pkill -f 'AggregationServer'
rm trash.txt
DIFF=$(diff output_1.txt expected_output_1.txt) #comparing with expected_output_1.txt as output is same since passed in txt file is same one used in test 1.
if ["$DIFF" != ""]
then
    echo "Heartbeat Mechanism test"
    echo "Test Successful - 1st GETClient (before CS terminated) - contaning feed from CS)_1"
fi
DIFF=$(diff output_2.txt expected_output_4.0.txt)
if ["$DIFF" != ""]
then
    echo "Test Successful - 2nd GETClient (12s after CS terminated, hence empty file)"
fi
rm output_1.txt
rm output_2.txt
rm replica.txt

#test to show heartbeat mechanism working, CS uploads content, client GETs, then CS is terminated.
#sleep script for 12 seconds (time to delete content belonging to a CS no longer providing heartbeats), client GETS again, feed is empty.

#NOTE - trash.txt is just to redirect output from AS or CS, since they are not important in this test and do not need to be printed to CLI.