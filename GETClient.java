import java.net.*;
import java.io.*;
import java.util.Vector;
import java.util.concurrent.TimeUnit;

public class GETClient 
{
    private int client_ID = 0;
    private Socket clientSocket;
    private ObjectOutputStream request;
    private ObjectInputStream content;
    private int retry_counter = 0; //this counter tracks how many times GETclient tries to resend request to AS
    private int reconnect_counter = 0; //this counter tracks how many times GETclient tries to reconnect to an AS (when AS is down)
    private int logicalClock = 0;
    private Vector<Content> returnedFeed;

    //default constructor
    public GETClient()
    {
    }
    
    //get method which will handle connecting to AS, sending the get request message, reading the AS response and printing the returned feed.
    public void get(String ip, int port) throws InterruptedException 
    {
        try
        {
            this.logicalClock++; //increment logical clock to signal event starting
            clientSocket = new Socket(ip, port);
            clientSocket.setSoTimeout(7000); //set timeout for readObject() to 7seconds
            request = new ObjectOutputStream(clientSocket.getOutputStream());    
            content = new ObjectInputStream(clientSocket.getInputStream());
            //create request object to send to AS
            Request request = new Request(this.logicalClock, this.client_ID,"get feed");

            returnedFeed = sendMessage(request);

            Request resp2 = (Request) content.readObject(); //read acknowledgement from AS which includes AS new logical clock
            logicalClock = resp2.updateTime(this.logicalClock); //update client logical clock based on return of AS
            System.out.println(resp2.command); //print message from AS
            printFeed(returnedFeed);
        }
        catch(Exception e)
        {
            if(e instanceof SocketTimeoutException) //if exception e == SocketTimeoutException (no object being sent to be read by readObject)
            {
                retry_counter++; 
                if(retry_counter <= 3)
                {
                    System.out.println("Server not responding, trying again");
                    get(ip, port); //recursively call get() 
                }
                else
                {
                    System.out.println("GET request to server failed");
                }
            }
            if(e instanceof java.net.ConnectException) //if exception e == connectionexception (AS is down or for some reason unable to connect to AS)
            {
                reconnect_counter++;
                logicalClock = 0; //reset LC to 0 so that it isn't incrementing every time we retry to connect
                if(reconnect_counter <= 3)
                {
                    System.out.println("Unable to connect to server, trying again");
                    TimeUnit.SECONDS.sleep(5); //sleep for 5 seconds so that there is time between retries
                    get(ip, port); //recursively call get()
                }
                else
                {
                    System.out.println("Unable to connect to AggregationServer. Something may be wrong with your connection or the AggregationServer may be down.");
                }
            }
            else
            {
            }
        }
    }

    //helper method to send request message to AS and get content feed back from AS in the form of the Vector
    public Vector<Content> sendMessage(Request msg) throws IOException, ClassNotFoundException
    {
            request.writeUnshared(msg);
            @SuppressWarnings("unchecked") //should be type safe as Content always compiled before runtime
            Vector<Content> resp = (Vector<Content>) content.readObject(); //server responds with a vector of content (the feed)
            return resp;
    }

    //helper method to iterate through returned Vector and print dereferenced "content" (String) from the Content objects kept on the vector. Method also formats out XML tags.
    public void printFeed(Vector<Content> feed)
    {
        for(Content item: feed)
        {
            String[] tmp = item.content.split("\n"); //split string into lines (carriage return delimiter) and save each line to a string array
            System.out.println();
            for(int i = 0; i < tmp.length; i++)
            { 
                int indexOf = tmp[i].indexOf(":") + 1; //for each line in the string array, get index of string after first ":"
                
                //if there is ":", index won't be negative
                if(indexOf > 0)
                {
                    System.out.println(tmp[i].substring(indexOf)); //print substring from indexOf till end of line
                }
                else //if no ":"
                {   
                    System.out.println();
                }
            }
        }
    }

    public static void main(String[] args) throws IOException, ClassNotFoundException, InterruptedException
    {
        GETClient client = new GETClient();
        String URL = args[0]; //get CLI input for servername 
        String URL_array[] = URL.split(":"); //split URL into servername:portnumnber with ":" as delimiter
        int port =  Integer.parseInt(URL_array[1]); //assign CLI port number to port variable
        client.get("127.0.0.1", port);
    }

}
