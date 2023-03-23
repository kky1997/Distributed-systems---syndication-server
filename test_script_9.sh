#!/bin/bash
rm replica.txt
java AggregationServer 4567 >> trash.txt &
sleep 2
java ContentServer AggregationServer:4567 sample4.txt 1 >> trash.txt &
sleep 2
java GETClient AggregationServer:4567 >> output_1.txt
pkill -f 'ContentServer AggregationServer:4567 sample.txt 1'
pkill -f 'AggregationServer'
sleep 2
java AggregationServer 4567 >> trash.txt &
sleep 2
java GETClient AggregationServer:4567 >> output_2.txt
sleep 2
java ContentServer AggregationServer:4567 sample5.txt 1 >> trash.txt &
sleep 10
java GETClient AggregationServer:4567 >> output_3.txt
pkill -f 'AggregationServer'
rm trash.txt
DIFF=$(diff output_1.txt expected_output_9.0.txt)
if ["$DIFF" != ""]
then
    echo "Replication/Persistence mechanism test extended (to show feed is always accurate, even during the initial start of AS and restore from replica.txt)"
    echo "More detailed explanation in both the ReadMe and in this script file"
    echo ""
    echo "Test Successful - 1st GET (before AS is killed - feed provided by CS_1 from sample4.txt input)"
fi
DIFF=$(diff output_2.txt expected_output_9.1.txt)
if ["$DIFF" != ""]
then
    echo "Test Successful - 2nd GET (after AS is restarted - feed restored from replica.txt)"
fi
DIFF=$(diff output_3.txt expected_output_9.2.txt)
if ["$DIFF" != ""]
then
    echo "Test Successful - 3rd GET (12seconds after AS has been restarted (and restored) and another CS has put())"
fi
rm output_1.txt
rm output_2.txt
rm output_3.txt
rm replica.txt

#A lot like test script 6, this test shows that the AS has replication and if the AS is killed then started again, it will still keep the feed provided by CS prior to it crashing (for 12 seconds). 
#CS is started once (line 5), so when AS is started again (line 11) there is no CS providing the feed again, the output to the 2nd client process (started at line 13) is from the replica.
#However at line 15 another CS puts() (input of sample5.txt), which is during the 12 seconds before the restored feed is purged. When the 3rd client process gets (line 17), the returned feed
#will only be the contents of sample5.txt. This shows that the restored feed was deleted after 12seconds of the AS restarting, but CS' whom put() during that time will not be affected;
#their content entries will persist after the restored feed has been deleted, and will persist until those CS' are terminated and the heartbeate mechanism deems their last heartbeat to be >12seconds
#ago. This shows that the feed is always accurate.