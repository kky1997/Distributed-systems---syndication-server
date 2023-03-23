import java.io.*;
import java.net.*;
import java.util.Vector;

//class to handle creating threads to handle requests from each connection (whether client or CS) to the AS
public class SocketHandler extends Thread
{
    private Socket cSocket;
    private ObjectOutputStream cOut;
    private ObjectInputStream cIn;

    //parameterised constructor
    public SocketHandler(Socket socket)
    {
        this.cSocket = socket;
    }

    //helper method to remove old entries from a CS ID that has uploaded new content, but less entries than it did previously. This method takes a "begin" index
    //of the last update entry belonging to a CS of particular ID and then iterates backwards through the entire AS feed till "begin", removing any extra
    //entries that were not part of the most recent put() by the CS.
    public void RemoveExtraID(int begin, int ID)
    {
        for(int j = AggregationServer.contentVector.size() - 1; j >= begin ; j--)
        {
            if (AggregationServer.contentVector.elementAt(j).contentServer_ID == ID)
            {
                AggregationServer.contentVector.remove(j);
            }
        }
    }

    //helper method that updates entries if a CS of ID "X" uploads new content while the AS already has entries from CS of ID "X". Does this replacing the entry at a given index
    //if the Content object at that index has the same ID as the CS calling this method. This method also removes each entry off the linkedlist sent in the Content object from CS.
    //Hence, as entries are removed from the linklist, they are added to the AS vector.
    public void UpdateEntries(Content new_Content, int index)
    {
        Content entry = new Content();
        entry.content = new_Content.entry_List.get(0);
        new_Content.entry_List.remove(0); //remove this entry from the arraylist (which will push all other elements to left, so always getting index 0)
        entry.contentServer_ID = new_Content.contentServer_ID;
        
        //replace old entry with new content
        AggregationServer.contentVector.set(index, entry);
    }

    //helper method that adds entries to the AS' vector when a CS connects and calls put()
    public void AddEntries(Content new_Content, int index)
    {
        Content entry = new Content();
        entry.content = new_Content.entry_List.get(index);
        entry.contentServer_ID = new_Content.contentServer_ID;
        AggregationServer.contentVector.add(entry); //add Content obj to vector
    }

    //helper method to take care of the heartbeat mechanism. Maps a time stamp (current time) to a CS' ID and adds them to a concurrent hashmap.
    //will create new entry in hashmap if ID doesn't already exist. If ID already on hashmap, this method will just update the time stamp to keep it current.
    public void HeartBeatHandler(Request req)
    {
        int ID = req.ID;
        if(AggregationServer.heartBeat.isEmpty() || !AggregationServer.heartBeat.containsKey(ID))
        {
            AggregationServer.heartBeat.put(ID, System.currentTimeMillis()); //add CS' ID and current time to hashmap on AS
        }
        else if(!AggregationServer.heartBeat.isEmpty() && AggregationServer.heartBeat.containsKey(ID))
        {
            AggregationServer.heartBeat.replace(ID, System.currentTimeMillis());
        }
    }

    //helper method to handle the client's GET() request. Updates the AS' current clock by comparing max(client and AS) + 1. Writes the feed to the Client (sends the feed vector)
    //then the AS will construct and send an acknowledgement message back to the client (containing the AS' new clock, the "SUCCESS" message).
    //finally the Client's request is polled from the PriorityBlockingQueue so that other requests (by other CS' or clients) can be fulfilled.
    public void GETRequestHandler(Request req, Request acknowledged) throws IOException
    {
        AggregationServer.AS_logicalClock.set(req.updateTime(AggregationServer.AS_logicalClock.get()));
        cOut.writeUnshared(AggregationServer.contentVector); //use outputstream to return content Queue to client content will eventually be text from content server (NOTE TO SELF - comment this line out when testing GETclient retrying request after timeout)
        acknowledged.logicalClock = AggregationServer.AS_logicalClock.get(); //get newest LC to send back
        acknowledged.command = "SUCCESS: enjoy your feed";
        cOut.writeUnshared(acknowledged);
        AggregationServer.pQueue.poll(); //remove request from queue
    }

    //helper method to handle any request which isn't a get() or put(). This method still updates the AS' logical clock as an event (request) still occured from either CS or client.
    //will write out an empty vector to the Client since it's request was invalid. Then an acknowledgment message (containing the AS' new clock and the "400" error message) is
    //constructed and sent to the requesting process. Finally the request is polled from the blockingqueue, allowing other Clients or CS' to have their requests handled.
    public void Error400Handler(Request req, Request acknowledged) throws IOException
    {
        AggregationServer.AS_logicalClock.set(req.updateTime(AggregationServer.AS_logicalClock.get()));
        cOut.writeUnshared(new Vector<Content>()); //use outputstream to return empty vector since request invalid
        acknowledged.logicalClock = AggregationServer.AS_logicalClock.get();
        acknowledged.command = "Error: 400";
        cOut.writeUnshared(acknowledged);
        AggregationServer.pQueue.poll(); //NOTE TO SELF CHANGED THIS from line 211 to 215
    }

    //run() method from runnable interface, will execute with .start() used to start socketHandler thread.
    //this method controls all core functionality of the AS; determining request type (get or put), heartbeat mechanism, AS feed upload and updating, 
    //and all return messages from AS to either CS or Client. Also handles synchronization and lamport clocks via adding requests to the PriorityBlockingQueue,
    //updating lamport clocks, and sending updated lamport clocks back to CS and client. The critical sections are protected by spinlocks which will spin until
    //the thread is at the front of the PriorityBlockQueue.
    @Override
    public void run()
    {
        try
        {
            cOut = new ObjectOutputStream(cSocket.getOutputStream());
            cIn = new ObjectInputStream(cSocket.getInputStream());
            Request acknowledgment = new Request(AggregationServer.AS_logicalClock.get(), 0, "");
            Request request = (Request) cIn.readObject();
            String command = request.command;  //read client request from object input stream

            while((command != null))
            {
                if("get feed".equals(command)) //if client sends get() request (which contains string "get feed")
                {
                    AggregationServer.pQueue.add(request); //add the request to the PriorityBlockingQueue
                    while(AggregationServer.pQueue.peek().ID != request.ID){} //spinlock till it is at front of queue
                    GETRequestHandler(request, acknowledgment);
                }
                else if("put request".equals(command)) //put CS sends put() request (which contains string "put request")
                {
                    AggregationServer.pQueue.add(request); 
                    while(AggregationServer.pQueue.peek().ID != request.ID){} 
                    AggregationServer.AS_logicalClock.set(request.updateTime(AggregationServer.AS_logicalClock.get())); 
                    if(AggregationServer.contentVector.isEmpty())
                    {
                        acknowledgment.logicalClock = AggregationServer.AS_logicalClock.get(); //get newest LC to send back to CS
                        acknowledgment.command = "201 - HTTP_CREATED";
                        cOut.writeUnshared(acknowledgment);
                    }
                    else
                    {
                        acknowledgment.logicalClock = AggregationServer.AS_logicalClock.get();
                        acknowledgment.command = "200";
                        cOut.writeUnshared(acknowledgment);
                    }
                    Content new_Content = (Content)cIn.readObject(); //read the Content object from CS, cast to Content obj
                    if(new_Content.content.isEmpty())
                    {
                        acknowledgment.logicalClock = AggregationServer.AS_logicalClock.get();
                        acknowledgment.command = "204 - Content is empty";
                        cOut.writeUnshared(acknowledgment);
                        AggregationServer.pQueue.poll(); //remove request from blockingqueue
                    }
                    else if(!new_Content.VerifyEntries()) //if entries are not valid (no title, id, or link)
                    {
                        acknowledgment.logicalClock = AggregationServer.AS_logicalClock.get();
                        acknowledgment.command = "500 - Internal server error";
                        cOut.writeUnshared(acknowledgment);
                        AggregationServer.pQueue.poll();
                    }
                    else
                    {
                        //if AS feed is 20 entries long already
                        if(AggregationServer.contentVector.size() >= 20)
                        {   
                            for(int i = 0; i < AggregationServer.contentVector.size(); i++)
                            {
                                //compare CS ID of entries in vector with the new entry
                                if(AggregationServer.contentVector.elementAt(i).contentServer_ID == new_Content.contentServer_ID && 
                                    !AggregationServer.contentVector.elementAt(i).content.equals(new_Content.entry_List.get(0)))
                                {
                                    UpdateEntries(new_Content, i);
                                    //if no more new entries to add, remove all previous content by CS with same ID, then break loop
                                    if(new_Content.entry_List.isEmpty())
                                    {
                                        RemoveExtraID(i + 1, new_Content.contentServer_ID);
                                        break;
                                    }
                                }
                                else if(AggregationServer.contentVector.elementAt(i).contentServer_ID == new_Content.contentServer_ID && 
                                    AggregationServer.contentVector.elementAt(i).content.equals(new_Content.entry_List.get(0))) //content is exactly the same, don't need to add it to AS feed again
                                {
                                    new_Content.entry_List.remove(0);
                                    if(new_Content.entry_List.isEmpty())
                                    {
                                        break;
                                    }
                                }
                            }
                        } 
                        //check again to see if feed is full, but also check if there are anymore entries to put onto contentVector
                        if(AggregationServer.contentVector.size() >= 20 && !new_Content.entry_List.isEmpty())
                        {     
                            for(int j = 0; j < new_Content.entry_List.size(); j++)
                            {
                                AggregationServer.contentVector.remove(0); //remove "j" elements from index 0 of vector like a "queue" (where j is number of new entries being added)
                                AddEntries(new_Content, j);
                            }
                        }
                        //if feed is not at capacity, but CS still has entries left to upload
                        else if(!new_Content.entry_List.isEmpty())
                        {
                            for(int i = 0; i < AggregationServer.contentVector.size(); i++)
                            {
                                if(AggregationServer.contentVector.elementAt(i).contentServer_ID == new_Content.contentServer_ID && 
                                    !AggregationServer.contentVector.elementAt(i).content.equals(new_Content.entry_List.get(0)))
                                {
                                    UpdateEntries(new_Content, i);
                                    if(AggregationServer.contentVector.size() >= 20)
                                    {
                                        AggregationServer.contentVector.remove(0);
                                    }
                                    if(new_Content.entry_List.isEmpty() && i+1 < AggregationServer.contentVector.size() 
                                        && AggregationServer.contentVector.elementAt(i + 1).contentServer_ID == new_Content.contentServer_ID)
                                    {
                                       RemoveExtraID(i + 1, new_Content.contentServer_ID);
                                       break;
                                    }
                                }
                                else if(AggregationServer.contentVector.elementAt(i).contentServer_ID == new_Content.contentServer_ID && 
                                    AggregationServer.contentVector.elementAt(i).content.equals(new_Content.entry_List.get(0)))
                                {
                                    new_Content.entry_List.remove(0);
                                    if(new_Content.entry_List.isEmpty())
                                    {
                                        break;
                                    }
                                }
                            }
                            //if CS ID not already in vector, just add entry to AS feed
                            for(int i = 0; i < new_Content.entry_List.size(); i++)
                            {
                                AddEntries(new_Content, i);
                                if(AggregationServer.contentVector.size() > 20)
                                {
                                    AggregationServer.contentVector.remove(0);
                                }
                            }
                        }
                        System.out.println("CS_ID polled from priority queue: " + AggregationServer.pQueue.poll().ID); //poll this CS' request from the blockingqueue now that it's complete
                    }
                }
                else if("heartbeat".equals(command))
                {
                    HeartBeatHandler(request);
                }
                else
                {
                    AggregationServer.pQueue.add(request);
                    while(AggregationServer.pQueue.peek().ID != request.ID){} 
                    Error400Handler(request, acknowledgment);
                }
                request = (Request) cIn.readObject(); //read request again within the loop
                command = request.command;
            }
            cIn.close();
            cOut.close();
            cSocket.close();
        }
        catch(Exception e) //catch is to catch net.socketException: Broken Pip (write failed) and IOexception when CS ends connection but don't want to print it
        {
        }
    }
}
