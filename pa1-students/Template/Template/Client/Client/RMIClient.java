package Client;

import Server.Interface.*;

import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.RemoteException;
import java.rmi.NotBoundException;

import java.util.*;
import java.io.*;

public class RMIClient extends Client
{
	private static String s_serverHost = "localhost";
        // recommended to hange port last digits to your group number
	private static int s_serverPort = 1099;
	private static String s_serverName = "Server";

	//TODO: ADD YOUR GROUP NUMBER TO COMPILE
	private static String s_rmiPrefix = "group_18_";

	public static void main(String args[])
	{	
		if (args.length > 0)
		{
			s_serverHost = args[0]; // 用于后续Registry的连接
		}
		if (args.length > 1)
		{
			s_serverName = args[1];  // 用于查找对方提供的Object名称
		}
		if (args.length > 2)
		{
			System.err.println((char)27 + "[31;1mClient exception: " + (char)27 + "[0mUsage: java client.RMIClient [server_hostname [server_rmiobject]]");
			System.exit(1);
		}

		// Get a reference to the RMIRegister
		try {
			RMIClient client = new RMIClient();
			client.connectServer();
			client.start();
		} 
		catch (Exception e) {    
			System.err.println((char)27 + "[31;1mClient exception: " + (char)27 + "[0mUncaught exception");
			e.printStackTrace();
			System.exit(1);
		}
	}

	public RMIClient()
	{
		super();
	}

	public void connectServer()
	{
		// use the default host,port to establish the connection
		connectServer(s_serverHost, s_serverPort, s_serverName);
	}

	public void connectServer(String server, int port, String name)
	{
		try {
			boolean first = true;
			while (true) { // 可以先启动客户端然后每隔500ms向服务端发起一次registry的查找请求，防止服务端没启动的情况
				try {
					Registry registry = LocateRegistry.getRegistry(server, port);
					// registry.lookup(...) used to looking for a specific server side obejct
					// NOTE: this object is a stub 
					m_resourceManager = (IResourceManager)registry.lookup(s_rmiPrefix + name);
					System.out.println("Connected to '" + name + "' server [" + server + ":" + port + "/" + s_rmiPrefix + name + "]");
					break;
				}
				catch (NotBoundException|RemoteException e) {
					if (first) {
						System.out.println("Waiting for '" + name + "' server [" + server + ":" + port + "/" + s_rmiPrefix + name + "]");
						first = false;
					}
				}
				Thread.sleep(500);
			}
		}
		catch (Exception e) {
			System.err.println((char)27 + "[31;1mServer exception: " + (char)27 + "[0mUncaught exception");
			e.printStackTrace();
			System.exit(1);
		}
	}
}

