#!/bin/bash
rm replica.txt
java AggregationServer 4567 >> trash.txt &
sleep 2
java ContentServer AggregationServer:4567 sample.txt 1 >> trash.txt &
sleep 2
java GETClient AggregationServer:4567 >> output_1.txt
pkill -f 'ContentServer AggregationServer:4567 sample.txt 1'
pkill -f 'AggregationServer'
sleep 2
java AggregationServer 4567 >> trash.txt &
sleep 2
java GETClient AggregationServer:4567 >> output_2.txt
sleep 2
pkill -f 'AggregationServer'
rm trash.txt
DIFF=$(diff output_1.txt expected_output_1.txt)
if ["$DIFF" != ""]
then
    echo "Replication/persistence test"
    echo "Test Successful - 1st GET (before AS is killed) - gets feed supplied by CS_1 (sample.txt input)"
fi
DIFF=$(diff output_2.txt expected_output_6.txt)
if ["$DIFF" != ""]
then
    echo "Test Successful - 2nd GET (after AS is restarted), this client gets the feed supplied restored to the AS from the replica.txt file"
fi
rm output_1.txt
rm output_2.txt
rm replica.txt
#this test shows that the AS has replication and if the AS is killed then started again, it will still keep the feed provided by CS prior to it crashing (for 12 seconds). 
#CS is only started once (line 5), so when AS is started again (line 11) there is no CS providing the feed again, the output to the 2nd client process (started at line 13) is from the replica.

#NOTE - trash.txt is just to redirect output from AS or CS, since they are not important in this test and do not need to be printed to CLI.