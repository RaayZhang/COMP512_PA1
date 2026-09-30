package Server.Common;

import java.io.Serializable;

public class TCPResponse implements Serializable {
    private static final long serialVersionID = 1L;
    private final long requestID;
    private final Object result;
    private final String error;

    // Eg: addFlight(...) => successful => new TCPResponse(id, true, null)
    public TCPResponse(long requestID, Object result, String error) {
        this.requestID = requestID;
        this.result = result; // can be different type, when the operation fail it would be null
        this.error = error; // when there no error it's null
    }

    public long getRequestId() {
        return requestID;
    }

    public Object getResult() {
        return result;
    }

    public String getError() {
        return error;
    }



}
