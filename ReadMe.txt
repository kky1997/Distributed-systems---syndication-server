Overview of System:

    My assignment 2 consists of 3 main entities:

    1. The AggregationServer (AS)

        This program aggregates a feed from entries provided by the Content Servers which connect to it and send a put() request. The AS also sends the aggregated feed
        to clients who make a get() request. All connections are facilitated by the sockets API. When the AS starts, it will begin listening on a specified port.
        Everytime a connection is established, the AS uses "SocketHandler" to create and start a new thread to handle each request (whether from client or CS).
        SocketHandler handles all the functionality that the AS must provide for the client and CS, like reading their requests, sending back acknowledgments,
        and adding content to the feed or retriving it from the feed to send.

        The AS also has features like replication/persistence (save feed state to file), read replicated feed on start up (if it exists yet), print it's lamport clock,
        and a heartbeat mechanism to ensure no feed entries are >12seconds old if CS hasn't sent a message.
        The AS also maintains the PriorityBlockingQueue which holds "Request" objects and facilitates synchronization between threads via this priorityqueue and lamport clock.

    2. ContentServer (CS)
 
        The CS reads a text file and gets the content of that text file, splitting the text it into various entries, storing them in a Content object. 
        It then sends a Request object to the AS (containing the CS' lamport clock & put message). Once the AS responds with an acknowledgment (containing it's lamport clock), 
        the CS  sends the Content object off to the AS through an object output stream. This object contains the entries/content, the CS' ID, and the put message header.
        The CS will read input from the AS' acknowledgment message that passes various codes (200, 201, 400 etc), and also the AS' updated lamport clock.
        The CS will print the relevent code, it's initial lamport clock, then it's updated lamport clock. Heartbeats are also sent to the AS every 3seconds
        to inform the AS that the CS is still connected and alive. If the CS is terminated, these heartbeats stop.
        The CS will also not allow an input file with >20 entries to be sent as this would exceed the limit of the feed. Files = 20 entries are fine.

    3. GET client

        The client sends a get() request to the AS, which in response, the AS will send an acknowledgment message (containing it's lamport clock) and the feed.
        Then the client will update it's clock using the clock sent in the acknowledgment from the AS and also print the feed.


    Content and Request are objects that are sent between the entities, either to pass messages/acknowledgments or to supply the feed content.

    All files have been compiled and run in java 8.



My System has the following functionality:

    -Persistent socket connections between AS and CS, where AS is able to service multiple CS' and clients at once (creating threads for each)

    Heartbeat Mechanism:
        between AS and CS which facilitates the removal of entries on the AS' feed from a specific CS (tracked by CS' ID) if it's process is shut down 
        and it hasn't sent a heartbeat for >12s. This is done through a concurrenthashmap kept on the AS which has (ID:timestamp) as its key:value pair.
        When a CS stays connected to the AS, it continues to send a message which provides it's current system time and it's ID. This is either added to the
        hashmap on the AS if the ID does not already exist, or the timestamp is simply updated if the ID already exists. If a CS disconnects and 12seconds pass,
        the AS will realise that the current system time is > 12seconds when compared to the last sent timestamp. Hence, it will remove that CS' ID:timestamp pair
        from the hashmap and also call a method to delete the entries on the AS feed which are linked to the terminated CS' ID.

    -CS is able to read file from CLI command, use regex to divide file into however many entries, and send to AS

    -AS is able to store up to 20 entries in the order that they have been sent by content servers

    Replication and persistence:
        every 2seconds, the current AS feed will be saved to a text file as a backup if the AS were to suddenly crash. On AS startup, the AS will start a thread which
        is responsible for checking if the replica.txt file exists in the current directory. If it does not, the AS will print "replica file is empty or has not been created yet."
        If it does, it will read the saved feed and then pass those entries into the AS feed. 
        As the AS has just been restored and no CS is currently connected, this restored feed will expire in 12seconds, allowing clients to get() this feed within 12seconds
        (which has been restored to it's state before the AS crashing/terminating). Once 12seconds has run out, the restored feed will be removed, following the rules
        of the heartbeat mechanism. Because reading the replica.txt means the feed is not being built by CS put(), the entries restored are given ID 0. This is why,
        the code at lines 178 - 187 in the AggregationServer.java file, check to see if the entries have an ID of 0. If they do, then it is safe to remove them after the 12seconds has passed.
        This is because during the 12seconds after the AS restarts and previous feed is restored, other CS' may try to connect and add their entries. Hence by checking if the ID is 0,
        we ensure that entries added by CS' during that initial 12seconds will not be deleted after. This is reflected in the replica.txt that is generated after the initial 12seconds
        after AS restart and replica.txt read occurs; the entries from the replica.txt will be deleted, but the entries put() by CS' during that 12seconds will remain.
        An added note is that "ReplicateMechanism" will only start after the thread for "ReadReplica" has terminated after its run(). This is so that there is no concurrent iteration
        over the vector (resulting in concurrentmodificationexception). This does mean however, that during the 12seconds after AS restart (with a feed restored from replica.txt), that 
        the replica.txt generated will not be updated. It will be updated once the 12seconds pass and the restored feed has been deleted. The AS will print "vector emptied" to notify
        when this has occured - the replica.txt will be back to its normal function, updating to show all the most recent entries from CS put()s.

    Text parsing:
        I have not implmeneted XML parsing, however the inputs are able to be formatted to how they appear in the input file and passed from CS to AS to client as Strings. 
        The client is able to recieve the String, use regex to remove any XML tags (title, ID, etc), and just print the entry to terminal.

    Client Fault Tolerance:
        Client has a read timeout, so after 7s, if AS has not written an obj to client, it will send it's get() request again, up to 3 times, before giving up.
        The client also has a connection timeout. If it's unable to connect to the AS (at port specified at AS startup), it will retry to connect every 5seconds up to 3 times before giving up.

    -All error codes are implemented

    -AS' contentVector, which holds the feed, has a max capacity of 20 entries and functions like a queue.

    Lamport Clock and synchronization:
        AS, CS, and client all maintain logical clocks which are sent to each other and updated using Lamport clock method

        requests from CS put() and client get() are seralised by placing them in a priorityblocking queue where priority is their LC, threads block (via a spinlock)
        until they are at front of queue or top priority. Once they have finished executing their critical section, their request is polled from the priorityqueue, and the
        next thread in line will stop spinlocking and proceed to execute it's function.

        currently, all entities will print their current logical clocks, so they can been seen to grow after each event. The AS will print it's lamport clock every 5seconds.
        This is so there is a visual representation of how it's incremented based on the number of requests it's getting from clients and CS'. 
        Both CS' and clients will also print their clocks; an initial clock when they first execute an event and then their modified clock after the AS returns it's clock.


How to run:

    AS requires 1 arg from CLI arg[0] = portnumber (eg. "java AggregationServer 4567).

    CS requires 3 args from CLI arg[0] = servername:portnumber, arg[1] = filepath, and arg[2] = CS ID (eg. "java ContentServer AggregationServer:4567 sample.txt 1)
    NOTE that the CS cannot have an ID < 1 (it will print "please use an ID that is greater than 0" if it is the case).

    GETclient requires 1 arg from CLI arg[0] = servername:portnumber (eg. "java GETclient AggregationServer:4567)

    multiple CS and clients can be started in additional terminals by passing differet IDs for CS through CLI

    alternatively, AS and CS can be pushed to background with "&" if the tester does not need to see the AS output (its current lamport clock) and CS output (any error code/system code
    returned by the AS), or the CS' lamport clock. This is how the testing scripts will function.
    

Testing:

    Files are all in a default directory, to compile just "javac *.java". Once the files are compiled, each script can be run via "./test_script_x.sh".
    There are 11 test scripts provided. Testing is done through starting the AS, CS, and Client in various ways to get an output
    and then comparing that output with the provided expected output. All this will be done by the scripts, which on completion and successful test, will print a message that will notify
    the tester that that test was successful.

    Each script will rm replica.txt file when started (this is just for safety as this file can interfere with the tests). 
    If replica.txt is not present the script will print "rm: cannot remove 'replica.txt': No such file or directory" and just continue regardless.
    The scripts also removes all output files after calling "diff". This is to keep the directory clean and less confusing. Since the script will print a message 
    to inform tester whether the test was successful, they are not neccessary anyway. 
    test_script_0 is an exception, it's output files will not be deleted at the end of the script. This is because the output must be mannually read (more detail in the script file).

    NOTE: Please check each script as each has a more detailed description of it's function and inteded purpose within it's file.
    NOTE: all test scripts will be running default portnumber 4567 for all processes

    test_script_0 - demonstartes synchronization between threads through use of lamport clocks, PriorityBlockingQueue, and spinlocking mutex.

    test_script_1 - demonstartes the basic functionality of AS start, CS put(), and Client get().

    test_script_2 - demonstartes multi CS funcationality, allowing multiple CS connections to build up the feed on the AS.

    test_script_3 - test to show multi client functionality and how get() feeds differ depending on order of get() and put()

    test_script_4.0 & 4.1 - shows the heartbeat mechanism by terminating a CS and sleeping for 12 seconds before another get() is called.

    test_script_5 - this script demonstartes the client's ability to retry connection (up to 3x) if the AS is killed.

    test_script_6 - demonstartes the replication/persistence of the AS 

    test_script_7 - test to show how the AS handles a feed at max capacity (20 entries).

    test_script_8 - shows that the 204, 500 & 400 error codes are working when a blank file, a file with invalid entries (no title, ID or link elemnts), or incorrect put()/get() message are passed

    test_script_9 - this script demonstartes the behaviour documented above under "replication and persistence". The AS is able to remove the entires restored from the back up file, but will not remove
                    any entries from a CS that calls put() during the first 12seconds of the AS restarting and restoring. Hence the feed is always accurate.


   The DummyCS and DummyGETClient supplied can be used to demonstarted when a process sends a request that is neither a valid get() nor put() message, resulting in error 400.
   Additionally, by commenting out lines 76 and 79 from the GETRequestHandler() in the SocketHandler.java, recompiling, and then attempting to have a 
   client to connect to the AS, the client's resend message mechanism can be demonstarted (due to no object being sent from AS for client to read - a readtimeout). NOTE that this is different
   to the retry to connect to AS mechanism if the AS is not running, demonstarted by test_script_5.

   Manual testing can be done by following "how to run" instructions above and supplying whatever CS file input (so long as they follow the input file format provided in the assignment spec).
   The replica.txt is updated in real time (every 2s), so it is very easy to see if put()s are functioning correctly by keeping the replica.txt file open and running put(). This will show real time changes
   to the feed as it's modified by various put()s, old entries deleted, etc.

   Finally, the scripts contain sleeps, so some of the scripts may take up to 15 seconds to complete.




