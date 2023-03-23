import java.io.*;

//serializable object to send through object input and output stream
public class Request implements Serializable, Comparable<Request>
{
    public Integer logicalClock = 0;
    public int ID;
    public String command;

    //parameterised constructor for Request
    public Request(int clock, int ID, String request)
    {
        this.logicalClock = clock;
        this.ID = ID;
        this.command = request;
    }

    //override method from comparable interface for the priorityqueue to be able to handle priorities based on natural ordering of logicalClock
    @Override
    public int compareTo(Request c) 
    {
        return this.logicalClock.compareTo(c.logicalClock);
    }

    //method to update the the lamport clocks based on the max between two ints + 1
    public int updateTime(int local_clock)
    {
        return Math.max(local_clock, this.logicalClock) + 1;
    }
}

