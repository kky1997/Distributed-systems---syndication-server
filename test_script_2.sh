#!/bin/bash
rm replica.txt
java AggregationServer 4567 >> trash.txt &
sleep 2
java ContentServer AggregationServer:4567 sample.txt 1 >> trash.txt &
sleep 2
java ContentServer AggregationServer:4567 sample3.txt 2 >> trash.txt &
sleep 2
java ContentServer AggregationServer:4567 sample2.txt 3 >> trash.txt &
sleep 2
java GETClient AggregationServer:4567 >> output.txt
pkill -f 'AggregationServer'
pkill -f 'ContentServer AggregationServer:4567 sample.txt 1'
pkill -f 'ContentServer AggregationServer:4567 sample3.txt 2'
pkill -f 'ContentServer AggregationServer:4567 sample2.txt 3'
rm trash.txt
DIFF=$(diff output.txt expected_output_2.txt)
if ["$DIFF" != ""]
then
    echo "Multi ContentServer test"
    echo "Test Successful"
    echo "AS is started, CS_1 put() sample.txt, CS_2 put() sample3.txt, and CS_3 put() sample2.txt. Finally Client called get(), getting a feed containing all entries from all 3 CS'"
fi
rm output.txt
rm replica.txt

#test to show multiple CS can provide content and build up feed on AS for client to GET.
#the output of of the client's get() will be the entries supplied by CS_1, CS_2, and CS_3
#in the order that they were put()

#NOTE - trash.txt is just to redirect output from AS or CS, since they are not important in this test and do not need to be printed to CLI.