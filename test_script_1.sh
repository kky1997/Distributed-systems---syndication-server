#!/bin/bash
rm replica.txt
java AggregationServer 4567 >> trash.txt &
sleep 2
java ContentServer AggregationServer:4567 sample.txt 1 >> trash.txt &
sleep 2
java GETClient AggregationServer:4567 >> output.txt
pkill -f 'AggregationServer'
pkill -f 'ContentServer'
rm trash.txt
DIFF=$(diff output.txt expected_output_1.txt)
if ["$DIFF" != ""]
then
    echo "Basic functionality test"
    echo "Test Successful"
    echo "AS is started, CS establishes connection and calls put() (sample.txt input). Finally, Client establishes connection with AS, calls get() for the feed from AS"
fi
rm output.txt
rm replica.txt

#test no.1: Simple test to prove AS starts, CS connects to AS and uploads content from "sample.txt", and client gets that feed from AS
#Client output is redirected to output.txt and diff is called to compare that with a provided file containing expected output.
#if diff successful, if statement will print "Test Successful". The output.txt file will be deleted to keep directory clean.

#NOTE - trash.txt is just to redirect output from AS or CS, since they are not important in this test and do not need to be printed to CLI.
