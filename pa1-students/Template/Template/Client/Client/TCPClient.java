package Client;

import Server.Common.TCPRequest;
import Server.Common.TCPResponse;
import Server.Interface.IResourceManager;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.rmi.RemoteException;
import java.util.Vector;
import java.util.concurrent.atomic.AtomicLong;

public class TCPClient extends Client {
    private final String middlewareHost;
    private final int middlewarePort;
    private final AtomicLong nextRequestID = new AtomicLong(1);

    public TCPClient(String middlewareHost, int middlewarePort) {
        this.middlewareHost = middlewareHost;
        this.middlewarePort = middlewarePort;
    }

    @Override
    public void connectServer() {
        // The socket is opened for each method call.
        m_resourceManager = new TCPResourceManagerProxy();
    }

    private Object sendRequest(String method, Object... arguments) throws RemoteException {
        long requestID = nextRequestID.getAndIncrement();
        TCPRequest request = new TCPRequest(requestID, method, arguments);

        try (Socket socket = new Socket(middlewareHost, middlewarePort);
             ObjectOutputStream output =new ObjectOutputStream(socket.getOutputStream())) {
            output.writeObject(request);
            output.flush();

            try (ObjectInputStream input =new ObjectInputStream(socket.getInputStream())) {
                TCPResponse response = (TCPResponse) input.readObject();
                if (response.getRequestId() != requestID) {
                    throw new RemoteException("Middleware response ID does not match request ID");
                }
                if (response.getError() != null) {
                    throw new RemoteException(response.getError());
                }
                return response.getResult();
            }
        } catch (RemoteException e) {
            throw e;
        } catch (Exception e) {
            throw new RemoteException("Cannot communicate with Middleware", e);
        }
    }

    private class TCPResourceManagerProxy implements IResourceManager {
        @Override
        public boolean addFlight(int flightNum, int seats, int price)throws RemoteException {
            return (Boolean) sendRequest("addFlight", flightNum, seats, price);
        }

        @Override
        public boolean addCars(String location, int count, int price) throws RemoteException {
            return (Boolean) sendRequest("addCars", location, count, price);
        }

        @Override
        public boolean addRooms(String location, int count, int price) throws RemoteException {
            return (Boolean) sendRequest("addRooms", location, count, price);
        }

        @Override
        public int newCustomer() throws RemoteException {
            return (Integer) sendRequest("newCustomer");
        }

        @Override
        public boolean newCustomer(int customerID) throws RemoteException {
            return (Boolean) sendRequest("newCustomer", customerID);
        }

        @Override
        public boolean deleteFlight(int flightNum) throws RemoteException {
            return (Boolean) sendRequest("deleteFlight", flightNum);
        }

        @Override
        public boolean deleteCars(String location) throws RemoteException {
            return (Boolean) sendRequest("deleteCars", location);
        }

        @Override
        public boolean deleteRooms(String location) throws RemoteException {
            return (Boolean) sendRequest("deleteRooms", location);
        }

        @Override
        public boolean deleteCustomer(int customerID) throws RemoteException {
            return (Boolean) sendRequest("deleteCustomer", customerID);
        }

        @Override
        public int queryFlight(int flightNum) throws RemoteException {
            return (Integer) sendRequest("queryFlight", flightNum);
        }

        @Override
        public int queryCars(String location) throws RemoteException {
            return (Integer) sendRequest("queryCars", location);
        }

        @Override
        public int queryRooms(String location) throws RemoteException {
            return (Integer) sendRequest("queryRooms", location);
        }

        @Override
        public String queryCustomerInfo(int customerID) throws RemoteException {
            return (String) sendRequest("queryCustomerInfo", customerID);
        }

        @Override
        public int queryFlightPrice(int flightNum) throws RemoteException {
            return (Integer) sendRequest("queryFlightPrice", flightNum);
        }

        @Override
        public int queryCarsPrice(String location) throws RemoteException {
            return (Integer) sendRequest("queryCarsPrice", location);
        }

        @Override
        public int queryRoomsPrice(String location) throws RemoteException {
            return (Integer) sendRequest("queryRoomsPrice", location);
        }

        @Override
        public boolean reserveFlight(int customerID, int flightNum)
                throws RemoteException {
            return (Boolean) sendRequest("reserveFlight", customerID, flightNum);
        }

        @Override
        public boolean reserveCar(int customerID, String location)
                throws RemoteException {
            return (Boolean) sendRequest("reserveCar", customerID, location);
        }

        @Override
        public boolean reserveRoom(int customerID, String location)
                throws RemoteException {
            return (Boolean) sendRequest("reserveRoom", customerID, location);
        }

        @Override
        public boolean bundle(int customerID, Vector<String> flightNumbers,
                              String location, boolean car, boolean room)
                throws RemoteException {
            return (Boolean) sendRequest("bundle", customerID, flightNumbers,
                    location, car, room);
        }

        @Override
        public String getName() throws RemoteException {
            return (String) sendRequest("getName");
        }
    }

    public static void main(String[] args) {
        if (args.length != 2) {
            System.err.println("Usage: TCPClient <middlewareHost> <middlewarePort>");
            return;
        }

        try {
            TCPClient client = new TCPClient(args[0], Integer.parseInt(args[1]));
            client.connectServer();
            client.start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
