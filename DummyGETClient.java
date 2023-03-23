import java.net.*;
import java.io.*;
import java.util.Vector;
import java.util.concurrent.TimeUnit;

public class DummyGETClient 
{
    private int client_ID = 0;
    private Socket clientSocket;
    private ObjectOutputStream request;
    private ObjectInputStream content;
    private int retry_counter = 0;
    private int reconnect_counter = 0;
    private int logicalClock = 0;
    private Vector<Content> returnedFeed;

    public DummyGETClient()
    {
    }
    
    //get method
    public void get(String ip, int port) throws InterruptedException 
    {
        
        try
        {
            this.logicalClock++; 
            clientSocket = new Socket(ip, port);
            clientSocket.setSoTimeout(7000);
            request = new ObjectOutputStream(clientSocket.getOutputStream());    
            content = new ObjectInputStream(clientSocket.getInputStream());
            Request request = new Request(this.logicalClock, this.client_ID,"wrong request"); //sending a message that isn't get() or put() to show error - 400 code works.

            returnedFeed = sendMessage(request);

            Request resp2 = (Request) content.readObject();
            logicalClock = resp2.updateTime(this.logicalClock); 
            System.out.println(resp2.command);
            printFeed(returnedFeed);

            
        }
        catch(Exception e)
        {
            if(e instanceof SocketTimeoutException) 
            {
                retry_counter++; 
                if(retry_counter <= 3) 
                {
                    System.out.println("Server not responding, trying again");
                    get(ip, port); 
                }
                else
                {
                    System.out.println("GET request to server failed");
                }
            }
            if(e instanceof java.net.ConnectException)
            {
                reconnect_counter++;
                logicalClock = 0;
                if(reconnect_counter <= 3)
                {
                    System.out.println("Unable to connect to server, trying again");
                    TimeUnit.SECONDS.sleep(5);
                    get(ip, port);
                }
                else
                {
                    System.out.println("Unable to connect to AggregationServer. Something may be wrong with your connection or the AggregationServer may be down.");
                }
            }
            else
            {
                System.err.println(e);
                e.printStackTrace();
            }
        }
    }

    
    public Vector<Content> sendMessage(Request msg) throws IOException, ClassNotFoundException
    {
            request.writeUnshared(msg);
            @SuppressWarnings("unchecked")
            Vector<Content> resp = (Vector<Content>) content.readObject();
            return resp;
    }

    
    public void printFeed(Vector<Content> feed)
    {
        for(Content item: feed)
        {
            String[] tmp = item.content.split("\n");
            System.out.println(); 
            for(int i = 0; i < tmp.length; i++)
            { 
                int indexOf = tmp[i].indexOf(":") + 1;
                
                if(indexOf > 0)
                {
                    System.out.println(tmp[i].substring(indexOf)); 
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
        DummyGETClient client = new DummyGETClient();
        String URL = args[0]; 
        String URL_array[] = URL.split(":");
        int port =  Integer.parseInt(URL_array[1]); 
        //client.client_ID = Integer.parseInt(args[1]);
        client.get("127.0.0.1", port); 
    }

}
