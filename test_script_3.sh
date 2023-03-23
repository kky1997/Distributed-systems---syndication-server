#!/bin/bash
rm replica.txt
java AggregationServer 4567 >> trash.txt &
sleep 2
java ContentServer AggregationServer:4567 sample.txt 1 >> trash.txt & 
sleep 2
java GETClient AggregationServer:4567 >> output_1.txt 
sleep 2
java ContentServer AggregationServer:4567 sample3.txt 2 >> trash.txt & 
sleep 2
java GETClient AggregationServer:4567 >> output_2.txt 
pkill -f 'AggregationServer'
pkill -f 'ContentServer AggregationServer:4567 sample.txt 1'
pkill -f 'ContentServer AggregationServer:4567 sample3.txt 2'
rm trash.txt
DIFF=$(diff output_1.txt expected_output_3.0.txt)
if ["$DIFF" != ""]
then
    echo "Multiclient test"
    echo "put(), get(), put(), get() interleaved"
    echo "Test Successful - 1st GETClient"
    echo "GETclient 1 - gets the content from CS_1 (sample.txt input)"
fi
DIFF=$(diff output_2.txt expected_output_3.1.txt)
if ["$DIFF" != ""]
then
    echo "Test Successful - 2nd GETClient"
    echo "GETclient 2 - gets the content from both CS_1 (sample.txt input) and CS_2 (sample3.txt input)"
fi
rm output_1.txt
rm output_2.txt
rm replica.txt

#test to show multi client, first client gets feed of CS(ID 1), second client gets feed of CS (ID 1) and CS (ID 2) combined
#this is because first get() is before CS_2 put()s and the second get() is after the CS_2 put()s.

#NOTE - trash.txt is just to redirect output from AS or CS, since they are not important in this test and do not need to be printed to CLI.