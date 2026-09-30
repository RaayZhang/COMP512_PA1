// -------------------------------
// adapted from Kevin T. Manley
// CSE 593
// -------------------------------

package Server.RMI;

import Server.Interface.*;
import Server.Common.*;

import java.rmi.NotBoundException;
import java.util.*;

import java.rmi.registry.Registry;
import java.rmi.registry.LocateRegistry;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;

public class RMIResourceManager extends ResourceManager 
{
	private static String s_serverName = "Server";
	//TODO: ADD YOUR GROUP NUMBER TO COMPLETE
	private static String s_rmiPrefix = "group_18_";

	public static void main(String args[])
	{
		if (args.length > 0)
		{
			s_serverName = args[0];
		}
			
		// Create the RMI server entry
		try {
			// Create a new Server object
			RMIResourceManager server = new RMIResourceManager(s_serverName);

			// Dynamically generate the stub (client proxy)
			// When the server start it will use exportObject(...) to create a stub and combine this with a specific name 
			// and register them onto the Registry. After the first time the client side use lookup(...) to obtain the stub
			// it could use this stub repeatedly without lookup(...) again.
			IResourceManager resourceManager = (IResourceManager)UnicastRemoteObject.exportObject(server, 0);

			// Bind the remote object's stub in the registry; adjust port if appropriate

			// Registry only used for the client side to lookup the stub object
			Registry l_registry;
			try {
				l_registry = LocateRegistry.createRegistry(1099);
			} catch (RemoteException e) { // 当两个服务器创建在同一个机器在同一个端口再创建registry就会失败，所以
				// 可以直接共享同一个端口，但是格子的ResourceManager仍然属于自己的服务器进程，通过不同名称查找可以获得不同stub
				l_registry = LocateRegistry.getRegistry(1099);
			}
			final Registry registry = l_registry;
			registry.rebind(s_rmiPrefix + s_serverName, resourceManager);

			// 当这个服务器进程结束，或者收到Ctrl+C时会移除Registry中的名称绑定 （不会关闭registry本身）
			// 防止后续还有其他客户端通过这个端口拿到stub, 之后还有新客户端lookup()这个名称就会报exception
			Runtime.getRuntime().addShutdownHook(new Thread() {
				public void run() {
					try {
						registry.unbind(s_rmiPrefix + s_serverName);
						System.out.println("'" + s_serverName + "' resource manager unbound");
					}
					catch(Exception e) {
						System.err.println((char)27 + "[31;1mServer exception: " + (char)27 + "[0mUncaught exception");
						e.printStackTrace();
					}
				}
			});                                       
			System.out.println("'" + s_serverName + "' resource manager server ready and bound to '" + s_rmiPrefix + s_serverName + "'");
		}
		catch (Exception e) {
			System.err.println((char)27 + "[31;1mServer exception: " + (char)27 + "[0mUncaught exception");
			e.printStackTrace();
			System.exit(1);
		}

	}

	public RMIResourceManager(String name)
	{
		super(name);
	}
}
