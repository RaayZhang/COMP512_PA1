package Server.TCP;

import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.Method;
import java.net.ServerSocket;
import java.net.Socket;

import Server.Common.ResourceManager;
import Server.Common.TCPRequest;
import Server.Common.TCPResponse;
import Server.Interface.IResourceManager;


/**
 * TCPResourceManager:
 * It's a server side TCP resource manager, once it receive a TCPRequest
 * it could call the method, and return TCPResponse
 */
public class TCPResourceManager extends ResourceManager{

    private static int TCPRM_PORT = 5000;
    
    public TCPResourceManager(String name) {
        super(name);
    }

    public void startServe(int port) throws Exception {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("TCP ResourceManager: " + getName() + " listening on " + port);
            while (true) {
                Socket socket = serverSocket.accept();
                new Thread(() -> handle(socket)).start(); 
            }
        }
    }

    private void handle(Socket socket) {
        // close the socket, inputStream and outputStream at the end
        try (Socket connect = socket; ObjectInputStream input = new ObjectInputStream(connect.getInputStream());) {

                    TCPRequest request = (TCPRequest) input.readObject();
                    long responseID = request.getRequestID();
                    TCPResponse response = null;
                    // dispatch the request from the message
                    // and call the method and get the result obj
                    try {
                        Object resposeObj = dispatch(request);
                        response = new TCPResponse(responseID, resposeObj, null);
                    } catch (Exception e) {
                        // operation fail
                        response = new TCPResponse(responseID, null, e.toString());
                    }

                    ObjectOutputStream output = new ObjectOutputStream(connect.getOutputStream());
                    output.writeObject(response);
                    output.flush();
                } catch (Exception e) {
                    e.printStackTrace();
                }
    }


    private Object dispatch(TCPRequest request) throws Exception{
        Object[] args = request.getArguments();
        // iterate through all avaliable methods of IResourceManager
        // it the method name .equals() reqest method name then it's legal
        for (Method m : ResourceManager.class.getMethods()) {
            if (request.getMethod().equals(m.getName()) && m.getParameterCount()==args.length) {
                    return m.invoke(this, args); 
            } 
        }
        throw new IllegalArgumentException("Unknown method or argument count: " + request.getMethod());
    }


    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            System.err.println("Usage: TCPResourceManager <name> <port>");
            return;
        }

        String name = args[0];
        int port = Integer.parseInt(args[1]);
        new TCPResourceManager(name).startServe(port); // we want the flight car, room listen at diff ports
    }




}
