import java.net.*;
import java.util.TimerTask;
import java.util.Vector;
import java.io.*;
import java.util.Timer;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;
public class AggregationServer 
{
    private ServerSocket serverSocket;
    public static Vector<Content> contentVector = new Vector<Content>(); //vector is synchronised
    public static ConcurrentHashMap<Integer, Long> heartBeat = new ConcurrentHashMap<Integer, Long>(); //using thread safe concurrenthashmap so multipe threads can operate on this single object

    //blocking queue so thread safe, holds requests from processes and seralises their order based on LC, using natural ordering of LC (smaller LC gets higher priority)
    public static PriorityBlockingQueue<Request> pQueue = new PriorityBlockingQueue<Request>();
    public static AtomicInteger AS_logicalClock = new AtomicInteger(); //atomic int so thread safe
    private ReadReplicaMechanism thread1 = new ReadReplicaMechanism(); //create a thread data member for ReadReplica (this is so I can call isAlive() on this specific thread to find out if it has finished its run()

    //non-parameterised constructor
    public AggregationServer()
    {
        AS_logicalClock.set(0);
    }
    
    //method to start the AS, along with some threads to handle printing AS lamport clock, to handle replication/persistence, to handle the heartbeat mechanism,
    //and to handle the creation of threads for each socket connection established with the AS. PrintLamportClocks and the Replication mechanism are timer tasks,
    //set to 5 and 2 seconds respectively.
    public void start(int p) throws IOException
    {
        int port = p; 
        serverSocket = new ServerSocket(port);
        thread1.start(); //read replica backup file when server has started (only if the file is not empty and exists)
        Timer timer = new Timer();
        timer.schedule(new PrintLamportClock(), 0, 5000);
        timer.schedule(new ReplicaMechanism(), 0, 2000); 
        new HeartbeatMechanism().start();
        while(true) 
        {
            new SocketHandler(serverSocket.accept()).start();
        }
    }

    //method to stop the AS
    public void stop() throws IOException
    {
        serverSocket.close();
    }
    
    //timertask class to print continue to print AS lamport clock so it can be seen as it evolves as processes connect
    class PrintLamportClock extends TimerTask
    {
        @Override
        public void run() 
        {
            System.out.println("Current AS Clock: " + AS_logicalClock);
        }
        
    }

    //class for the timed replication mechanism extending  timertask (implements runnable)  and using the helper replicaWrite() method
    class ReplicaMechanism extends TimerTask
    {
        //implemetned run() from runnable interface, create thread to run following methods
        @Override
        public void run() 
        {
            //this thread will only run once the readreplica thread is finished, this is so there is no concurrent iteration on vector, resulting in concurrentmodificationexception
            if(!thread1.isAlive()) 
            {
                replicaWrite();
            }
        }

        //helper method to save contentVector (the feed on AS) to a file
        public void replicaWrite() 
        {
            try
            {
                File replica = new File("replica.txt"); //create a file object
                replica.createNewFile(); //create physical file
                if(replica.exists())
                {
                    File tmp = new File("tmp"); //create a tmp file
                    tmp.createNewFile();
                    FileWriter replicator = new FileWriter("tmp"); 
                    for(Content items : AggregationServer.contentVector)
                    {
                        replicator.write(items.content + "\n");
                        replicator.write("CS ID: " + items.contentServer_ID + "\n");
                        replicator.write("----------------" + "\n");
                    }
                    replicator.close();
                    replica.delete();
                    tmp.renameTo(new File("replica.txt")); //rename tmp to replica.txt             
                }
            }
            catch(Exception e)
            {
                System.out.println("Error has occured");
                e.printStackTrace();
            }
        }

    }

    //threaded class to take care of checking "heartBeat" hashmap, checking if last heartbeat sent by a CS was >12seconds ago, if so delete from content vector
    class HeartbeatMechanism extends Thread
    {
        @Override
        public void run()
        {
            while(true)
            {
                timedDelete();
            }
        }

        //helper method to delete content >12 seconds old  (after not reciving a heartbeat from it's corresponding CS)
        public void timedDelete()
        {
            if(!heartBeat.isEmpty())
            {
                //Iterate through heartBeat hashmap, getting time stamp stored at each key (CS ID), if time stamp is > 12sec, means CS hasn't sent heartbeat in >12sec, hence delete entry/content from vector
                for(int ID : heartBeat.keySet())
                {
                    if(System.currentTimeMillis() - heartBeat.get(ID) >= 12000)
                    {
                        heartBeat.remove(ID); 
                        System.out.println("removed from hashmap");
                        removeContent(ID);
                        System.out.println("removed from vector");
                        
                    }
                }
            }
        }

        //helper method called in timedDelete() to remove content from the vector that is >12sec without a heartbeat
        public void removeContent(int ID)
        {
            //traverse vector (iterating backwards as we are removing elements so size() is changing)
            for(int i = contentVector.size() - 1; i >=0; i--)
            {
                if(contentVector.elementAt(i).contentServer_ID == ID)
                {
                    contentVector.remove(i);
                }
            }
        }
    }

    //this is another threaded class to handle reading of the replica.txt backup file
    class ReadReplicaMechanism extends Thread
    {
        final Long time = System.currentTimeMillis(); //get an initial arbitray final system time
        public int listSize = 0;

        @Override
        public void run()
        {
            try
            {
                readReplica(); //invoke readReplica() helper method
            }
            catch(Exception e)
            {
                System.err.println(e);
            }
            while(true)
            {
                if(AggregationServer.contentVector.isEmpty()) //if the replica.txt file is empty or does not exist break this loop
                {
                    break;
                }
                //compare the current system time with the snapshot final "time", this is so even though entries stored on the replica.txt file won't be linked to any heartbeat, 
                //they are still purged after 12 seconds. This gives the client 12seconds to reconnect and get the previous feed right before the AS crashed/terminated
                else if(System.currentTimeMillis() - time >= 12000 && !AggregationServer.contentVector.isEmpty())  
                {
                    for(int i = 0; i < listSize; i++)
                    {
                        if(AggregationServer.contentVector.elementAt(i).contentServer_ID == 0)
                        {
                            AggregationServer.contentVector.remove(i);
                        }
                    }
                    System.out.println("vector emptied");
                    break;
                }
            }
            
        }

        //method used to read the replica.txt file, splits it into entries, adds them to the AS feed data structure
        public void readReplica() throws FileNotFoundException, IOException
        {
            File file = new File("replica.txt");
            boolean check = file.exists(); //checking to see the file exists
            boolean isEmpty = file.length() == 0; //checking to see the file is devoid of text
            
            if(check && !isEmpty)
            {
                Content content = new Content();
                BufferedReader br = new BufferedReader(new FileReader("replica.txt"));
                String line = "";
                while((line = br.readLine()) != null) 
                {
                    if(!line.startsWith("CS ID:")) //to skip this line I added to the replica.txt to make the file more readable, but is not part of the feed entry
                    {
                        content.content += line + "\n";
                    }
                }      
                br.close();
                content.entry_List = content.RegexSplitter(); //call regexSplitter method to split content into seperate entries and store each entry on the entry_list list
                for(int i = 0; i < content.entry_List.size(); i++) //create seperate entry (Content) objects for each entry and add those to the content datastrcuture on the AS
                {
                    Content entry = new Content();
                    entry.content = content.entry_List.get(i);
                    AggregationServer.contentVector.add(entry);
                }
                listSize = content.entry_List.size();
            }
            else if(isEmpty || !check)
            {
                System.out.println("replica file is empty or has not been created yet");
            }
        }
    }

    public static void main(String[] args) throws IOException
    {
        AggregationServer AS = new AggregationServer();
        int port = Integer.parseInt(args[0]);
        AS.start(port);
    }
}
