# TCP implementation

This implementation runs the travel reservation service over TCP sockets. The client sends requests to the middleware; the middleware routes single-resource operations to one resource manager (RM) and coordinates operations involving multiple RMs.

## Requirements and directory layout

- A JDK with `javac` and `java` available in PowerShell.
- Run the commands below from `pa1-students\Template\Template`, the directory containing both `Client` and `Server`.
- Open a separate terminal for each long-running process. Keep the three RMs and the middleware running while using the client.

The relevant source files are:

| File | Purpose |
| --- | --- |
| `Server\Server\Common\TCPRequest.java` | Serializable request: request ID, method name, and arguments. |
| `Server\Server\Common\TCPResponse.java` | Serializable reply: request ID, result, or error. |
| `Server\Server\TCP\TCPResourceManager.java` | Listens for TCP requests, calls the actual `ResourceManager` methods, and replies. Run three separate instances. |
| `Server\Server\TCP\TCPMiddleware.java` | Accepts client requests, routes them to the correct RM, and coordinates customer and bundle operations. |
| `Client\Client\TCPClient.java` | Reuses the existing interactive `Client` command parser; its local `IResourceManager` adapter sends method calls to the middleware over TCP. |

The TCP implementation also uses the starter files `Client\Client\Client.java`, `Client\Client\Command.java`, `Server\Server\Interface\IResourceManager.java`, and the classes in `Server\Server\Common`. `Client.java` includes the empty-line input fix.

## Compile (PowerShell)

In a terminal at `pa1-students\Template\Template`:

```powershell
$serverFiles = Get-ChildItem .\Server\Server -Recurse -Filter *.java -File |
    Where-Object { $_.Name -notlike '._*' } |
    Select-Object -ExpandProperty FullName

$clientFiles = Get-ChildItem .\Client\Client -Filter *.java -File |
    Where-Object { $_.Name -notlike '._*' } |
    Select-Object -ExpandProperty FullName

New-Item -ItemType Directory -Force .\out | Out-Null
javac -d .\out $serverFiles $clientFiles
```

The `._*` filter excludes macOS metadata files that are not Java source. A `javac` note about unchecked operations in `TCPMiddleware.java` is a warning; compilation succeeds if there are no `error:` lines. Recompile after source changes, then restart the process whose code changed. If a source file was deleted, rebuild `$serverFiles` and `$clientFiles` before recompiling.

## Run (five PowerShell terminals)

Each command below runs from `pa1-students\Template\Template` and uses `out` as the classpath root. Start the three RMs first, then the middleware, then the client.

**Terminal 1 — Flights RM:**

```powershell
java -cp .\out Server.TCP.TCPResourceManager Flights 5001
```

**Terminal 2 — Cars RM:**

```powershell
java -cp .\out Server.TCP.TCPResourceManager Cars 5002
```

**Terminal 3 — Rooms RM:**

```powershell
java -cp .\out Server.TCP.TCPResourceManager Rooms 5003
```

**Terminal 4 — middleware (listens on port 5000):**

```powershell
java -cp .\out Server.TCP.TCPMiddleware 5000 localhost localhost localhost 5001 5002 5003
```

Middleware arguments are `<listenPort> <flightHost> <carHost> <roomHost> <flightPort> <carPort> <roomPort>`. Replace `localhost` with the relevant hostnames when the processes run on separate machines. Each RM must have a reachable listening port.

**Terminal 5 — interactive client:**

```powershell
java -cp .\out Client.TCPClient localhost 5000
```

Client arguments are `<middlewareHost> <middlewarePort>`. On separate machines, supply the middleware machine's hostname instead of `localhost`.

### Quick check

Enter one command per line in the client (choose unused IDs if these were already used in the running RMs):

```text
AddCustomerID,73021
AddFlight,93021,2,150
ReserveFlight,73021,93021
QueryFlight,93021
QueryCustomer,73021
DeleteCustomer,73021
QueryFlight,93021
```

The first `QueryFlight` should show one available seat; after deleting the customer, the second should show two. The bill should contain one reservation for `flight-93021` at `$150`.

## Request and reply flow

1. `Client.java` parses a command and calls a method on its `IResourceManager` field. The adapter in `TCPClient.java` converts this call to a `TCPRequest` and sends it through a socket to the middleware.
2. `TCPMiddleware.java` reads the method name and arguments. For a single resource, it selects Flights, Cars, or Rooms. It opens a separate socket to that RM and sends another `TCPRequest` with a middleware-generated request ID.
3. The selected `TCPResourceManager` dispatches the request to its actual `ResourceManager` method. It puts the return value or error in a `TCPResponse` and sends it to the middleware.
4. The middleware checks the RM response ID and returns a `TCPResponse` using the **original client request ID**. The client checks that ID and hands the result back to `Client.java`, which prints it.

For `newCustomer`, `deleteCustomer`, `queryCustomerInfo`, and `bundle`, the middleware makes multiple RM calls and combines their results. Customer records exist in all three RMs. A bundle checks availability and then sends individual reservation requests to the relevant RMs.

The current protocol uses one request and one response per TCP connection. The middleware accepts a client connection and hands its work to another thread, allowing the accept loop to receive more clients while an RM reply is pending. RMs also handle accepted connections in separate threads. The middleware uses Flight, Car, and Room locks in that order for operations spanning multiple RMs.

## Current consistency limits

The middleware's locks coordinate requests handled by that middleware instance. They do not provide a transaction across independent RMs. A connection failure partway through a multi-RM customer operation or bundle may leave partial state; the bundle precheck alone cannot guarantee rollback if a later reservation fails. Concurrent direct access to an RM, or running multiple middleware instances against the same RMs, needs additional synchronization and consistency handling.
