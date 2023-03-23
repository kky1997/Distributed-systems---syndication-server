#!/bin/bash
rm replica.txt
java GETClient AggregationServer:4567 >> output_1.txt &
sleep 6
java AggregationServer 4567 >> trash.txt &
sleep 2
java GETClient AggregationServer:4567 >> output_2.txt
pkill -f 'AggregationServer'
rm trash.txt
DIFF=$(diff output_1.txt expected_output_5.0.txt)
if ["$DIFF" != ""]
then
    echo "Retry connection to AS mechanism test"
    echo "Test Successful - client tries to reconnect 2 times"
    echo "GETClient is unable to connect as AggregationServer has not been started yet"
    echo ""
fi
DIFF=$(diff output_2.txt expected_output_5.1.txt)
if ["$DIFF" != ""]
then
    echo "Test Successful - AS is started, client is able to establish connection on it's 3rd retry"
    echo "output is just the acknowledgment message as AS has no CS' providing content yet"
fi
rm output_1.txt
rm output_2.txt
rm replica.txt

#test to show the retry to connect feature of the client.
#client tries to connect to AS before it is up and begins to retry. On the 3rd retry, AS is brought online so client is able to connect.
#Since the client was able to connect and call get(), the AS returns a feed, however the feed is empty, hence why the expected file is just the
#acknowledgement message.

#NOTE - trash.txt is just to redirect output from AS or CS, since they are not important in this test and do not need to be printed to CLI.