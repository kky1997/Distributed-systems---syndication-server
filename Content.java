import java.io.*;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

//serializable object to send through object input and output stream
public class Content implements Serializable
{
    public int contentServer_ID = 0;
    public String header = "";
    public String content = ""; //save text from file in this string
    public List<String> entry_List; //split "content" into individual entries and store on this linkedlist

    //default constructor
    public Content()
    {
    }

    //method that will split the text from the input file (passed in at CLI) stored in "content" string, into individual entries, using "carriage return + entry" as delimiter
    public LinkedList<String> RegexSplitter()
    {
        List<String> tmp = new LinkedList<String>();
        if(!content.startsWith(" entry"))
        {
            tmp = (Arrays.asList(content.split("\n" + "entry")));
        }
        LinkedList<String> linkedList = new LinkedList<String>(tmp);
        return linkedList; 
    }

    //method to check each entry in the list and verify the entry is valid (contains those elements), if it does not return false, if it does return true.
    public boolean VerifyEntries()
    {
        for(int i = 0; i < entry_List.size(); i++)
        {
            if(!entry_List.get(i).contains("title") || !entry_List.get(i).contains("link") || !entry_List.get(i).contains("\n" + "id:"))
            {
                return false;
            }
        }
        return true;
    }

    //method which adds with header to every put() request which specifies Content-Length (number of entries) and content type (hard coded as 'Text')
    public void CreateHeader()
    {
        String put_header = "PUT /atom.xml HTTP/1.1" + "\n" + "User-Agent: ATOMClient/1/0" + "\n" + "Content-Type: Text" + "\n" + "Content-Length: " + entry_List.size() + " entries" + "\n" + "\n" + "<?xml version='1.0' encoding='iso-8859-1' ?>" + "<feed xml:lang=en-US xmlns=http://www.w3.org/2005/Atom>"; 
        this.header = put_header;
    }

    //print method to print content
    public void print()
    {
        System.out.println(content);
    }
}


