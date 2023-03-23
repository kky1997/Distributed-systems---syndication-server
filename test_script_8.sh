#!/bin/bash
rm replica.txt
java AggregationServer 4567 >> trash.txt &
sleep 2
java ContentServer AggregationServer:4567 sample_blank.txt 1 >> output.txt
sleep 1
echo >> output.txt
sleep 2
java ContentServer AggregationServer:4567 sample_invalid.txt 2 >> output.txt
sleep 2
echo >> output.txt
echo "ContentServer process Error:" >> output.txt
sleep 2
java DummyCS AggregationServer:4567 sample_invalid.txt 3 >> output.txt
echo >> output.txt
echo "Client process Error:" >> output.txt
sleep 2
java DummyGETClient AggregationServer:4567 sample_invalid.txt 4 >> output.txt
pkill -f 'AggregationServer'
pkill -f 'ContentServer AggregationServer:4567 sample_blank.txt 1'
pkill -f 'DummyCS AggregationServer:4567 sample_blank.txt 3'
pkill -f 'ContentServer AggregationServer:4567 sample_invalid.txt 2'
pkill -f 'DummyGETClient AggregationServer:4567 sample_invalid.txt 4'
DIFF=$(diff output.txt expected_output_8.txt)
if ["$DIFF" != ""]
then
    echo "Test to show the Error code acknowledgment messages from AS to CS"
    echo "Test Successful - 204, 500, and 400 (from both CS and client) error code"
fi
rm replica.txt
rm trash.txt
rm output.txt

#this test shows the error codes 204 and 500 being returned to the CS and printed.
#the 204 is due to the sample_blank.txt being passed 
#the 500 is due to the sample_invalid.txt being passed
#the 400 is due to incorrect get() and put() messages being passed from the Dummy CS and Client

#NOTE - trash.txt is just to redirect output from AS or CS, since they are not important in this test and do not need to be printed to CLI.