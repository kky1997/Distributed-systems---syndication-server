#!/bin/bash
rm replica.txt
java AggregationServer 4567 >> trash.txt &
sleep 2
java ContentServer AggregationServer:4567 sample.txt 1 >> trash.txt &
sleep 2
java ContentServer AggregationServer:4567 sample3.txt 2 >> trash.txt &
sleep 2
java GETClient AggregationServer:4567 >> output_1.txt
pkill -f 'ContentServer AggregationServer:4567 sample.txt 1'
sleep 12
java GETClient AggregationServer:4567 >> output_2.txt
pkill -f 'AggregationServer'
rm trash.txt
DIFF=$(diff output_1.txt expected_output_4.1.txt) #comparing with expected_output_1.txt as output is same since passed in txt file is same one used in test 1.
if ["$DIFF" != ""]
then
    echo "Heartbeat Mechanism test extended"
    echo "Test Successful - 1st GETClient (before CS_1 is terminated) - this client gets feed supplied by both CS_1 and CS_2"
fi
DIFF=$(diff output_2.txt expected_output_4.2.txt)
if ["$DIFF" != ""]
then
    echo "Test Successful - 2nd GETClient (12s after CS_1 terminated, only CS_2 content in get())"
fi
rm output_1.txt
rm output_2.txt
rm replica.txt

#this is an extension of test 4.0 as it now shows two CS' being started and only one being terminated. This means that the first GET (line 9) will get the content of both
#CS 1 & 2, but after CS_1 is terminated and 12seconds pass (for the heartbeat mechanism), the second GET(line 12) will only get the content of CS_2.

#NOTE - trash.txt is just to redirect output from AS or CS, since they are not important in this test and do not need to be printed to CLI.