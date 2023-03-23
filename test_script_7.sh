#!/bin/bash
rm replica.txt
java AggregationServer 4567 >> trash.txt &
sleep 2
java ContentServer AggregationServer:4567 sample_20_entry.txt 1 >> trash.txt &
sleep 2
java GETClient AggregationServer:4567 >> output_1.txt
sleep 2
java ContentServer AggregationServer:4567 sample.txt 2 >> trash.txt &
sleep 2
java GETClient AggregationServer:4567 >> output_2.txt
sleep 2
java ContentServer AggregationServer:4567 sample2.txt 3 >> trash.txt &
sleep 2
java GETClient AggregationServer:4567 >> output_3.txt
pkill -f 'AggregationServer'
pkill -f 'ContentServer AggregationServer:4567 20_entry_sample.txt 1'
rm trash.txt
DIFF=$(diff output_1.txt expected_output_7.0.txt)
if ["$DIFF" != ""]
then
    echo "Test to show AS feed behaviour when it is at maximum capacity (20 entries)"
    echo "Test Successful - 1st GET (20 entries from a single CS)"
fi
DIFF=$(diff output_2.txt expected_output_7.1.txt)
if ["$DIFF" != ""]
then
    echo "Test Successful - 2nd GET (20 entries after CS (ID 2) has made a put)"
fi
DIFF=$(diff output_3.txt expected_output_7.2.txt)
if ["$DIFF" != ""]
then
    echo "Test Successful - 2nd GET (20 entries after CS (ID 3) has made a put)"
fi
rm output_1.txt
rm output_2.txt
rm output_3.txt
rm replica.txt

#test to show that the max capcity of the feed is 20 entries, and if other CS' try to add x number of entries to the feed, the first x number of entries on the feed (oldest) will be
#removed and the new entries added to the back.

#NOTE - trash.txt is just to redirect output from AS or CS, since they are not important in this test and do not need to be printed to CLI.