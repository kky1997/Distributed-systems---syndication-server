import java.net.*;
import java.util.TimerTask;
import java.io.*;
import java.util.Timer;
public class DummyCS 
{
    private Socket contentSocket;
    private ObjectOutputStream put;
    private ObjectInputStream confirmation;
    private int contentServer_ID = 0;
    private int logicalClock = 0;
    
    //default constructor
    public DummyCS()
    {
    }

    //method to send the put request to AS. This method will handles everything including; connecting to AS, sending the request, sending the content, and reciving the response from
    //the AS. This method prints all relevent error/system codes depending on the return message from AS.
    public void put(int p, Content content)
    {
        int port = p;
        try
        {
            this.logicalClock++;//increment logical clock to signal event starting
            System.out.println("Initial LC: " + logicalClock);
            contentSocket = new Socket("127.0.0.1", port);
            put = new ObjectOutputStream(contentSocket.getOutputStream());
            confirmation = new ObjectInputStream(contentSocket.getInputStream());
            Request request = new Request(this.logicalClock, this.contentServer_ID, "wrong request"); //sending request, ID and logical clock
            String serverResponse = putRequest(request); //message sent to AS for put request and the returned server's response
            System.out.println(serverResponse);
            if(("201 - HTTP_CREATED".equals(serverResponse))||("200".equals(serverResponse)))
            {
                System.out.println("uploading content");
            } 
            else
            {
                System.out.println("failed to upload content");
            }

            //print new LC after AS sends acknowledgement and LC back
            System.out.println("CS ID: " + this.contentServer_ID);
            System.out.println("New LC: " + logicalClock + "\n");

            //seralization of content object
            put.writeUnshared(content);

            //if entry blank, print appropriate response
            if(content.content.isEmpty())
            {
                Request response = (Request)confirmation.readObject();
                System.out.println(response.command);
            }

            //if entries invalid, print appropriate response
            else if(!content.VerifyEntries())
            {
                Request response = (Request)confirmation.readObject();
                System.out.println(response.command);
            }
            else
            {
                Timer timer = new Timer();
                timer.schedule(new heartBeat(), 0, 3000); //timer to keep sending heartbeat ever 3 seconds to AS
            }
        }
        catch(Exception e)
        {
            if(e instanceof java.lang.ClassCastException)
            {
                System.out.println("Error: 400");
            }
            else
            {
                System.err.println(e);
                e.printStackTrace();
            }
        }
    }

    //herlp method to send messages and return the response message from AS
    public String putRequest(Request msg) throws IOException, ClassNotFoundException
    {
        put.writeUnshared(msg);
        Request resp = (Request)confirmation.readObject();

        //UPDATE CS logical clock when AS sends acknowledgment back
        logicalClock = resp.updateTime(this.logicalClock);

        return resp.command;
    }

    //helper method to read text files and then convert them into Content object to be sent to AS.
    //works by dereferencing Content object's "content" variable and append text from txt file to it
    public void ATOMTextReader(String pathToFeedFile, Content text) throws FileNotFoundException, IOException
    {
        BufferedReader br = new BufferedReader(new FileReader(pathToFeedFile));
        String line = ""; 
        while((line = br.readLine()) != null)
        {
            text.content += line + "\n"; //dereference Content object's "content" variable and append text from txt file to it
        }      
        br.close();
    }

    //helper method to send heartbeat to AS. Works by sending "heartbeat" as the request message as well as the CS' ID that is sending it.
    public void heartBeatMechanism()
    {
        try
        {
            Request heartRequest = new Request(this.logicalClock, this.contentServer_ID,"heartbeat");
            put.writeUnshared(heartRequest); 
        }
        catch(Exception e)
        {
            System.out.println(e);
            e.printStackTrace();
        }
    }

//timer class to facilitate the CS sending heartbeats to the AS. Calls the heartBeatMechanism() helper method and is set to a timer.
class heartBeat extends TimerTask
{
    public void run()
    {
        try
        {
            heartBeatMechanism();
        }
        catch(Exception e)
        {
            System.err.println(e);
            e.printStackTrace();
        }
    }
}
    public static void main(String[] args) throws Exception
    {
  
        DummyCS CS = new DummyCS();
        String URL = args[0]; //get the servername from CLI
        String URL_array[] = URL.split(":"); //split the URL format (servername:portnumber) into an array where index 0 is servername and index 1 is portnumber using ":" as delimiter
        int port =  Integer.parseInt(URL_array[1]); //get the port number from the URL_array
        Content c1 = new Content();
        String fileLocation = args[1]; //get the filepath from CLI
        File file = new File(fileLocation);
        CS.ATOMTextReader(file.getAbsolutePath(), c1); //read the text from the pass in file
        c1.entry_List = c1.RegexSplitter(); //split the text into entries

        //if statement to check if a CS is trying to send >20 entries at once
        if(c1.entry_List.size() > 20)
        {
            System.out.println("Error: trying to send too many entries");
            System.exit(0);
        }
        c1.CreateHeader();
        c1.contentServer_ID = Integer.parseInt(args[2]);
        CS.contentServer_ID = c1.contentServer_ID;
        CS.put(port, c1);


    }
}