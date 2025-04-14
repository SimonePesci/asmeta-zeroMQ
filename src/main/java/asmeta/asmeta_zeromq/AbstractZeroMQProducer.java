package asmeta.asmeta_zeromq;

import java.lang.reflect.Type;
import java.util.Map;
import java.util.Objects;

import org.zeromq.ZContext;
import org.zeromq.ZMQ.Poller;
import org.zeromq.ZMQ.Socket;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

public abstract class AbstractZeroMQProducer {
    protected final String publisherAddress;
    protected final String triggerAddress;
    protected final String responseAddress;

    protected ZContext context;
    protected Socket publisher;
    protected Socket triggerSubscriber;
    protected  Socket responseSubscriber;
    protected Poller poller;
    protected final Gson gson;

    protected final Type mapStringObjectType = new TypeToken<Map<String,Object>>(){}.getType();
    protected final Type mapStringStringType = new TypeToken<Map<String,String>>(){}.getType();

    protected int simulationId = -1;

    /**
     * Creates a new AbstrackZeroMQProducer
     * @param publisherAddress The address where to publish (PUB Socket)
     * @param triggerAddress The address to subscribe to receive the trigger (SUB Socket)
     * @param responseAddress The address where to to get the response (SUB Socket)
     */
    protected AbstractZeroMQProducer(String publisherAddress, String triggerAddress, String responseAddress) {
        this.publisherAddress = Objects.requireNonNull(publisherAddress, "Publisher address can not be null");
        this.triggerAddress = Objects.requireNonNull(triggerAddress, "Publisher address can not be null");
        this.responseAddress = Objects.requireNonNull(responseAddress, "Publisher address can not be null");
        this.gson = new Gson();
    }

    // Abstract Methods

    /**
     * Start the specified application
     * @return A unique identifier for the simulation/process instance (e.g., Asmeta ID).
     * @throws Exception if initialization fails.
     */
    protected abstract int initializeApplication() throws Exception;

    /**
     * Processes the initial trigger message received on the triggerAddress
     * @param triggerMessage The parsed content of the trigger message (Map<String, String>).
     * @return The monitored variables required for the first simulation step,
     *         or null if the trigger is invalid or should be ignored.
     */
    protected abstract Map<String,String> processInitialTrigger(Map<String, String> triggerMessage);
}
