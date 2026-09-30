package Server.TCP;

import Server.Common.TCPRequest;
import Server.Common.TCPResponse;

import java.util.HashMap;
import java.util.Map;
import java.util.Vector;
import java.util.concurrent.atomic.AtomicLong;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * TCPMiddleware
 * Entire process involve 5 steps
 * 1: Client send a TCPRequest to the Middleware (specific port)
 * 2: Middlware read the method name and parameter and send it to the eg: FlightTCPResourceManager
 * 3: FlightResourceManager receive the TCP request call the method
 * 4: RM send the response object (this includes the return obj, error msg...) to MiddleWare
 * 5: Middleware send it back as TCPResponse to client side
 */

public class TCPMiddleware {
    private final String flightHost;
    private final String carHost;
    private final String roomHost;
    private final int flightPort;
    private final int carPort;
    private final int roomPort;
    
    // 和RMI一样设置三把锁】
    private final Object flightLock = new Object();
    private final Object carLock = new Object();
    private final Object roomLock = new Object();

    private final AtomicLong nextRMRequestID = new AtomicLong(1);

    /**
     * 每个client连接交给独立线程，等待RM的回复
     * 监听线程同时接收其他client request
     */
    public TCPMiddleware(String flightHost, String carHost, String roomHost,
         int flightPort, int carPort, int roomPort) {
        this.flightHost = flightHost;
        this.carHost = carHost;
        this.roomHost = roomHost;
        this.flightPort = flightPort;
        this.carPort = carPort;
        this.roomPort = roomPort;
    }


    /**
     * Listen to the request from client
     * @param port : port for client request
     */

    public void startServe(int port) throws Exception {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("TCP Middleware listening on " + port);
        
            while (true) {
                Socket socket = serverSocket.accept();
                new Thread(() -> handle(socket)).start(); // new thread handle with specific socket connection
            }
        
        }
    }

    private void handle(Socket socket) {
        try (Socket client = socket;
            ObjectInputStream input = new ObjectInputStream(client.getInputStream());
        ) {
            TCPRequest req = (TCPRequest) input.readObject();
            TCPResponse response;

            try {
                Object result = dispatch(req); // forwarding to serverRM and take the result
                response = new TCPResponse(req.getRequestID(), result, null);
            } catch (Exception e) {
                response = new TCPResponse(req.getRequestID(), null, e.toString());
            }

            try (ObjectOutputStream output = new ObjectOutputStream(client.getOutputStream())) {
                output.writeObject(response);
                output.flush();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    /**
     * 
     * @param req TCP reqeust from the client side involve (method_name, parameters)
     * @return the return from the server side 
     * @throws Exception
     */
    private Object dispatch(TCPRequest req) throws Exception{
        String method = req.getMethod();
        Object[] args = req.getArguments();
        // Decide which 
        switch (method) {
            // Flight type method (call() would be used in flight() to connect with the FlightRM port)
            case "addFlight":
            case "deleteFlight":
            case "reserveFlight": synchronized (flightLock) { // Required a lock
                            return flight(method, args); // For all those methods that would change the status 
            }

            case "queryFlight":
            case "queryFlightPrice": return flight(method, args);

            // Cars type method
            case "addCars":
            case "deleteCars":
            case "reserveCar": synchronized (carLock) {
                return  car(method, args);
            }
            case "queryCars":
            case "queryCarsPrice": return car(method, args);

            // Rooms type method
            case "addRooms":
            case "deleteRooms":
            case "reserveRoom": synchronized(roomLock) {
                return  room(method, args);
            }
            case "queryRooms":
            case "queryRoomsPrice": return room(method, args);

            // bundle type method need all 3 RMs
            // 这些方法的需要的锁会在方法内声明
            case "newCustomer":
                if (args.length == 0) {
                    return  newCustomer();
                } else if (args.length == 1) {
                    return  newCustomer( (Integer)args[0]);
                }
                break; // illegal
            
            case "deleteCustomer":
                if (args.length == 1) return deleteCustomer((Integer) args[0]);
                break;
            case "queryCustomerInfo":
                if (args.length==1) return queryCustomerInfo( (Integer) args[0]);
                break;
            case "bundle":
                if (args.length == 5) return bundle((Integer)args[0],(Vector<String>) args[1], (String)args[2], (Boolean)args[3], (Boolean)args[4]);
                break;
            case "getName":
                if (args.length == 0) {
                    return "You are connect with Middleware.";
                }

        }
        throw new IllegalArgumentException("Unknown method or arguments: " + method);
        
    }

    private boolean newCustomer(int customerID) throws Exception{
        synchronized (flightLock) {
            synchronized (carLock) {
                synchronized (roomLock) {
                    if (! (Boolean)flight("newCustomer", customerID)) {
                        return false; // fail to init customer at flightRM
                    }
                    if (! (Boolean)car("newCustomer", customerID)) {
                        flight("deleteCustomer", customerID); // 防止不一致删除刚初始的数据
                        return false;
                    }
                    if (! (Boolean)room("newCustomer", customerID)) {
                        flight("deleteCustomer", customerID);
                        car("deleteCustomer", customerID);
                        return false;
                    }
                    return true;
                }
            }
        }
    }

    /**
     * newCustomer() without argument we could init this customer and get it ID
     *  at one of the RM eg: FlightRM, then for the rest RM we init new customer within this customerID
     */
    private int newCustomer() throws Exception {
       synchronized (flightLock) {
            synchronized (carLock) {
                synchronized (roomLock) {
                    int customerID = (Integer) flight("newCustomer"); // obatin the new customerID

                    if (!(Boolean)car("newCustomer", customerID)) {
                        flight("deleteCustomer", customerID);
                        return -1;
                    }

                    if (!(Boolean)room("newCustomer", customerID)) {
                        car("deleteCustomer", customerID);
                        flight("deleteCustomer", customerID);
                        return -1;
                    }

                    return customerID;
                }
            }
        }
    }


    private boolean deleteCustomer(int customerID) throws Exception {
        synchronized (flightLock) {
            synchronized (carLock) {
                synchronized (roomLock) {
                    boolean f = (Boolean)flight("deleteCustomer", customerID);
                    boolean c = (Boolean)car("deleteCustomer", customerID);
                    boolean r = (Boolean)room("deleteCustomer", customerID);
                    return f && c && r;
                }
            }
        }
    }

    private String queryCustomerInfo(int customerID) throws Exception {
        // 取出所有RM中的bill信息
        // 忽略所有header， 和RMI中一样的代码直接用
      String[] bills = {(String )flight("queryCustomerInfo", customerID), (String)car("queryCustomerInfo", customerID),
                (String) room("queryCustomerInfo", customerID)};

        // RM 没有该客户时返回 ""。
        if (bills[0].isEmpty()) {
            return "";
        }

        StringBuilder result = new StringBuilder("Bill for customer "+ customerID + "\n");
        int total = 0;

        for (String bill : bills) {
            String[] lines = bill.split("\\R");

            // 每份账单第 0 行是 "Bill for customer <id>"。
            for (int i = 1; i < lines.length; i++) {
                String line = lines[i].trim();
                if (line.isEmpty()) {
                    continue;
                }

                result.append(line).append('\n');
                int count = Integer.parseInt(line.substring(0, line.indexOf(' ')));
                int price = Integer.parseInt(line.substring(line.lastIndexOf('$') + 1));
                total += count * price;
            }
        }

        return result.append("Total: $").append(total).toString();
    }


    private boolean bundle(int customerID, Vector<String> flightNumbers,
        String location, boolean bookCar, boolean bookRoom) throws Exception {

        synchronized (flightLock) {
            synchronized (carLock) {
                synchronized (roomLock) {
                    if (flightNumbers == null|| flightNumbers.isEmpty()) {
                        return false; // 必须订机票
                    }

                    if (((String) flight("queryCustomerInfo", customerID)).isEmpty()|| ((String) car("queryCustomerInfo", customerID)).isEmpty()
                            || ((String) room("queryCustomerInfo", customerID)).isEmpty()) {
                        return false;
                    }

                    if (bookCar && (Integer) car("queryCars", location) <= 0) {
                        return false;
                    }

                    if (bookRoom && (Integer) room("queryRooms", location) <= 0) {
                        return false;
                    }

                    Map<String, Integer> seatsNeeded = new HashMap<>();
                    for (String number : flightNumbers) {
                        seatsNeeded.merge(number, 1, Integer::sum);
                    }

                    for (Map.Entry<String, Integer> entry: seatsNeeded.entrySet()) {
                        int flightNumber =Integer.parseInt(entry.getKey());
                        int available = (Integer)flight("queryFlight", flightNumber);
                        if (available < entry.getValue()) {
                            return false;
                        }
                    }
                    for (String number : flightNumbers) {
                        if (! (Boolean)flight("reserveFlight",customerID,Integer.parseInt(number))) {
                            return false;
                        }
                    }
                    if (bookCar && ! (Boolean)car("reserveCar", customerID, location)) {
                        return false;
                    }
                    if (bookRoom && ! (Boolean)room("reserveRoom", customerID, location)) {
                        return false;
                    }

                    return true;
                }
            }
        }
    }


    /**
     * Send a reqeust to eg FlightRM, and wait for the reply (this thread will be blocked)
     * @return the desire result in handle function (we will form an response and send it back to client)
     */
    private Object call(int RMport, String RMhost, String method, Object ... args) throws Exception{
        try (Socket socket = new Socket(RMhost, RMport);
            ObjectOutputStream output = new ObjectOutputStream(socket.getOutputStream())) {
            
            // 这里用的requestID是否应该和client发的TCP request 的id一样呢？
            // 因为middleware和server side的bundle request会涉及多个RM的请求，所以可能不好分辨
            long rmReqID = nextRMRequestID.getAndIncrement();
            TCPRequest request = new TCPRequest(rmReqID, method, args);
            output.writeObject(request);
            output.flush();

            try (ObjectInputStream input = new ObjectInputStream(socket.getInputStream())) {
                TCPResponse response = (TCPResponse) input.readObject();

                if (response.getRequestId() != rmReqID) {
                    throw new IllegalStateException("the requestID doesn't match the responseID");
                }

                if (response.getError() != null) { // fail to call the method at serverRM
                    throw new Exception(response.getError());
                }

                return response.getResult();
            }

        }
    }

    private Object flight(String method, Object... args) throws Exception {
        return call(flightPort, flightHost, method, args);
    }

    private Object car(String method, Object... args) throws Exception {
        return call(carPort, carHost, method, args);
    }

    private Object room(String method, Object... args) throws Exception {
        return call(roomPort, roomHost, method, args);
    }

     public static void main(String[] args) throws Exception {
        if (args.length != 7) {
            System.err.println("Usage: TCPMiddleware <listenPort> "
            + "<flightHost> <carHost> <roomHost> "
            + "<flightPort> <carPort> <roomPort>");
            return;
        }

        int listenPort = Integer.parseInt(args[0]);

        TCPMiddleware middleware = new TCPMiddleware(args[1], args[2], args[3],
        Integer.parseInt(args[4]),Integer.parseInt(args[5]),Integer.parseInt(args[6]));

        middleware.startServe(listenPort);
}

    
}