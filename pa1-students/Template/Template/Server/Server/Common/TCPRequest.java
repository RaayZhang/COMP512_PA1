package Server.Common;
import java.io.Serializable;

    

public class TCPRequest implements Serializable {
        private static final long serialVersionID = 1L;

        private final long requestID;
        private final String method;
        private final Object[] arguments;

        // different command required various amount of arguments
        public TCPRequest(long requestID, String method, Object... arguments) {
            // the request might get response in different order
            // in order to reach the correct client, we need to have the requestID
            this.requestID = requestID;
            this.method = method; // specific command name
            this.arguments = arguments; // place all required parameters inside arguments
        }

        public long getRequestID() {
            return requestID;
        }

        public String getMethod() {
            return method;
        }

        public Object[] getArguments() {
            return arguments;
        }
}

