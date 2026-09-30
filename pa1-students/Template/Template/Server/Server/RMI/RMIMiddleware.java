package Server.RMI;

import Server.Common.ResourceManager;
import Server.Interface.IResourceManager;

import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.ObjID;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Vector;
import java.util.Map;

public class RMIMiddleware extends ResourceManager {
    // 运行三个不同的ResourceManager obejct每个只负责独立的服务(flight, room, car)
    // 每个特定的ResourceManager都有自己的m_data
    // Client: 持有IResourceManager类型的Middleware stub
    // Middleware: 假装实现IResourceManager接口中的方法像是addFlight(), reserverFlight()
    // 实际上它只是持有3个IResourceManager类型的stub, 每次Client需要调用方法，他都会调用特定方法
    // 然后特定方法会通过stub调用真实server端的 eg Car 的ResourceManager 的方法

    /**
     * Client 持有 Middleware 的 IResourceManager stub
        → 调用 addCars("Montreal", 3, 100)
    Middleware 真正执行自己的 addCars(...)
        → 调用自己持有的 Cars RM stub 的 addCars(...)
    Cars RM 的真实 RMIResourceManager 对象
        → 执行继承自 ResourceManager 的 addCars(...)
        → 修改 Cars RM 自己的 m_data
     */


    // We need to handle the simultaneous request situation. When 2 client conduct the 
    // same reservation for one resource at almost the same time we should ensure the synchronization
    // make sure the following case won't happen
    // 当我检查完，发现资源还在，然后开始预订，但是就在这两个操作之间资源被别人预约了
    // 查询方法可以不需要锁

    // 方案1： 所有flight操作用一把锁（除了查询这些不改变状态的操作），所有car 一把，所有room一把
    // 像是bundle， newCustomer, deleteCustomer操作需要全部3把
    // NOTE： 需要3个锁的情况可能出现deadlock， 需要检查拿锁顺序
    // 查询需不需要锁，有没有可能查询到中间状态？ （目前没加锁）
    // 方案2:  所有操作用一把锁

    private static String m_rmiPrefix = "group_18_";
    private static String m_middlewareName = "Middleware"; // 在main里面用.限制一下因为端口一样有可能输入起到同样名字拿到其他stub?
    private static int serverPort = 1099;

    private final IResourceManager flightRM;
    private final IResourceManager carRM;
    private final IResourceManager roomRM;

    // define 3 locks
    private final Object flightLock = new Object();
    private final Object carLock = new Object();
    private final Object roomLock = new Object();


    public static void main(String[] args) {
        if (args.length != 3) {
            System.err.println("Usage: RMIMiddleware <flightHost> <carHost> <roomHost>");
            return;
        }

        try {
            
            RMIMiddleware middleware = new RMIMiddleware(m_middlewareName, args[0], args[1], args[2]);
            IResourceManager stub = (IResourceManager) UnicastRemoteObject.exportObject(middleware, 0);
            Registry registry;
            try {
                registry = LocateRegistry.createRegistry(serverPort);
            } catch (RemoteException e) {
                registry = LocateRegistry.getRegistry(serverPort);
            }

            registry.rebind(m_rmiPrefix + m_middlewareName, stub);
            System.out.println("Middleware ready: " + m_rmiPrefix + m_middlewareName);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public RMIMiddleware(String name, String flightHost, String carHost, String roomHost) throws Exception {
        super(name);
        flightRM = connectRM(flightHost, "Flights"); // must be same as what server side defined
        carRM = connectRM(carHost, "Cars");
        roomRM = connectRM(roomHost, "Rooms");
    }

    private IResourceManager connectRM(String host, String name) throws Exception{
        
            boolean first = true;
            while (true) {
                try {
                    Registry registry = LocateRegistry.getRegistry(host, serverPort);
                    IResourceManager rm = (IResourceManager) registry.lookup(m_rmiPrefix+name);
                    System.out.println("Connected to " + name + " RM on " + host); // 这个显示在哪里？
                    return rm;  
                } catch (RemoteException | NotBoundException e) {
                    if (first) {
                        System.out.println("Waiting for " + name + " RM on " + host);
                        first = false;
                    }
                    Thread.sleep(500);
                }
            }
    }


    /**    
     * We should make sure all 3 RM has the this new customer, or they all without this customer
     */
    // 三把锁防止其他客户端在创建尚未完成时操作这个客户。
    @Override
    public boolean newCustomer(int customerID) throws RemoteException {
        synchronized(flightLock) {
            synchronized (carLock) {
                synchronized(roomLock) {
                    if (!flightRM.newCustomer(customerID)) {
                        return false; // means this customerID already existed inside all 3 RM
                    }

                    if (!carRM.newCustomer(customerID)) { // at this stage if there is connection problem make this operation return false
                        flightRM.deleteCustomer(customerID);  // we should rollback to make sure they all have the same status
                        return false;
                    }

                    if (!roomRM.newCustomer(customerID)) {
                        carRM.deleteCustomer(customerID);
                        flightRM.deleteCustomer(customerID);
                        return false;
                    }

                    return true;
                }
            }
        }
    }

    /** same idea as the previous method */
    // For the client side once it receive the customerID = -1
    // it need to handle with this case !!
    @Override
    public int newCustomer() throws RemoteException {
        synchronized(flightLock) {
            synchronized(carLock) {
                synchronized(roomLock) {
                    int customerID = flightRM.newCustomer();

                    if (!carRM.newCustomer(customerID)) {
                        flightRM.deleteCustomer(customerID);
                        return -1;
                    }

                    if (!roomRM.newCustomer(customerID)) {
                        carRM.deleteCustomer(customerID);
                        flightRM.deleteCustomer(customerID);
                        return -1;
                    }
                    return customerID;
                }
            }
        }
    }  
    
    


    /**    
     * Once we are going to delete certain customerID, it should be removed from all 3 RM
     * Problem1:*************************************************************************
     * How to handle the case when there is connection issue and only the customerID at flightRM been removed??
     * Problem2: ***********************************************************************
     * How to distinguish the fail delete and the case that it don't have that customerID in the certain RM??
     * 返回false后再逐一检查RM.queryCustomerInfo(xxx) 结果是否全部为空 -> yes 则认为删除成功
     * -> no 则认为是因为fail delete 导致false，有的没有删除干净，可以再次调用deleteCustomer(xxx)尝试删除
     */
    @Override 
    public boolean deleteCustomer(int customerID) throws RemoteException {
         synchronized(flightLock) {
            synchronized(carLock) {
                synchronized(roomLock) {
                    boolean flightsDeleted = flightRM.deleteCustomer(customerID);
                    boolean carsDeleted = carRM.deleteCustomer(customerID);
                    boolean roomsDeleted = roomRM.deleteCustomer(customerID);

                    return flightsDeleted && carsDeleted && roomsDeleted;
                }
            }
         }
    }



    /**    
     * need to return the summation for bill at 3 different reservation
     *  and we need to obtain them from m_data of 3 ResourceManager
     * Querying a customer returns the list of reserved items, as well as the total cost to the customer。
     */
    @Override 
    public String queryCustomerInfo(int customerID) throws RemoteException {
        // the customer.getBill() returns a String type, the pattern is "count reservableItemKey price"
        // when the customer doesn't exist eg: flightRM.queryCustomerInfo(...) will return ""
        // if it exist but no reservation it should return "Bill for customer xxx"
        String[] bills = { flightRM.queryCustomerInfo(customerID), carRM.queryCustomerInfo(customerID), roomRM.queryCustomerInfo(customerID)};
        if (bills[0].isEmpty()) {
            return ""; // customerID not exist
        }
        String s = "Bill for customer " + customerID + "\n"; // when there to many String need to add use StringBuilder() will be better
        int total = 0;  // the getCount() return int type?

        for (String bill : bills) {
            // Each bill could includes multiple reservedItems
            String[] reservedItems = bill.split("\\R");

            // the first line is "Bill for customer xxx" we just ignore it
            for (int i=1; i<reservedItems.length; i++) {
                String reservedItem = reservedItems[i].trim();
                if (reservedItem.isEmpty()) {
                    continue;
                } else {
                    s += reservedItem + "\n";
                    // the first entry is count
                    int count = Integer.parseInt(reservedItem.substring(0, reservedItem.indexOf(' ')));
                    // the third entry is price
                    int price = Integer.parseInt(reservedItem.substring(reservedItem.lastIndexOf("$") + 1));
                    total += count * price;
                }
            }

        }
        s += "Total: $" + total;  // total 是int type有溢出风险
        return s;  
    
    }

    @Override
    public boolean reserveFlight(int customerID, int flightNum)  throws RemoteException {
         synchronized(flightLock) {
            return flightRM.reserveFlight(customerID, flightNum);
         }
    }

    @Override
    public boolean reserveCar(int customerID, String location) throws RemoteException {
        synchronized(carLock) {
            return carRM.reserveCar(customerID, location);
        }
    }


    @Override
    public boolean reserveRoom(int customerID, String location) throws RemoteException {
        synchronized(roomLock) {
            return  roomRM.reserveRoom(customerID, location);
        }
    }



    @Override 
    public boolean addFlight(int flightNum, int seats, int price) throws RemoteException {
        synchronized(flightLock) {
            return flightRM.addFlight(flightNum, seats, price);
        }
    }

    @Override
    public boolean addCars(String location, int count, int price) throws RemoteException {
        synchronized(carLock) {
            return carRM.addCars(location, count, price);
        }
    }

    @Override
    public boolean addRooms(String location, int count, int price) throws RemoteException {
        synchronized(roomLock) {
            return roomRM.addRooms(location, count, price);
        }
    }


    // Returns the number of cars available at a location
    @Override 
    public int queryCars(String location) throws RemoteException {
        return carRM.queryCars(location); // use the carRM stub to call the method
    }

    // Returns the amount of rooms available at a location
    @Override
    public int queryRooms(String location) throws RemoteException {
        return roomRM.queryRooms(location);
    }

    // Returns the number of empty seats in this flight
    @Override
    public int queryFlight(int flightNum) throws RemoteException {
        return flightRM.queryFlight(flightNum);
    }

    // Returns price of cars at this location
    @Override 
    public int queryCarsPrice(String location) throws RemoteException {
        return carRM.queryCarsPrice(location);
    }

    // Returns room price at this location
    @Override
    public int queryRoomsPrice(String location) throws RemoteException {
        return roomRM.queryRoomsPrice(location);
    }


    // Returns price of a seat in this flight
    @Override
    public int queryFlightPrice(int flightNum) throws RemoteException {
        return flightRM.queryFlightPrice(flightNum);
    }


    @Override
    public boolean deleteCars(String location) throws RemoteException {
        synchronized(carLock) {
            return carRM.deleteCars(location);
        }
    }

    @Override
    public boolean deleteRooms(String location) throws RemoteException {
        synchronized(roomLock) {
            return roomRM.deleteRooms(location);
        }
    }

    @Override
    public boolean deleteFlight(int flightNum) throws RemoteException {
        synchronized(flightLock) {
            return flightRM.deleteFlight(flightNum);
        }
    }


    /**    
     * Book 1+ flights, and possibly a car (optional) and/or room (optional)
     */
    @Override 
    public boolean bundle(int customerID, Vector<String> flightNumbers, String location, boolean car, boolean room) throws RemoteException {
        synchronized(flightLock) {
            synchronized(carLock) {
                synchronized(roomLock) {
                    // check if the flight number not null or empty
                    if (flightNumbers == null || flightNumbers.isEmpty()) {
                        return false;
                    }

                    // check if the customerID exist in all 3 RM
                    if (flightRM.queryCustomerInfo(customerID).isEmpty() || carRM.queryCustomerInfo(customerID).isEmpty()
                    || roomRM.queryCustomerInfo(customerID).isEmpty()) {
                        return false;
                    }

                    if (car && carRM.queryCars(location) <= 0) {
                        return false;
                    }

                    if (room && roomRM.queryRooms(location) < 1) {
                        return false;
                    }

                    // Count how many seat require for certain flight eg : [1002, 1002 ,1003, 1003, 1003]
                    Map<String, Integer> flightTable = new HashMap<>();
                    for (String flightNumber : flightNumbers) {
                        flightTable.put(flightNumber, flightTable.getOrDefault(flightNumber, 0)+1);
                    }

                    // check if there is enough seat for the certain flight
                    for (Map.Entry<String, Integer> entry : flightTable.entrySet()) {
                        if (flightRM.queryFlight(Integer.parseInt(entry.getKey())) < entry.getValue()) {
                            return false; // don't have enough seats
                        }
                    }

                    // when they all have avaliable seats
                    for (String flightNumber : flightNumbers) {
                        if (!flightRM.reserveFlight(customerID, Integer.parseInt(flightNumber))) {
                            return false;
                        }
                    }
                    

                    if (car && !carRM.reserveCar(customerID, location)) {
                        return false;
                    }


                    if (room && !roomRM.reserveRoom(customerID, location)) {
                        return false;
                    }
                    return true;
                }
            }
        }

    }
    






    


    
}
