package partycast.model;

public class OperationRejectedException extends Exception {

    public OperationRejectedException() {
        super();
    }

    public OperationRejectedException(String message) {
        super(message);
    }

    public OperationRejectedException(String message, Throwable cause) {
        super(message, cause);
    }

    public OperationRejectedException(Throwable cause) {
        super(cause);
    }
}