package asmeta.asmeta_zeromq;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;

import org.asmeta.runtime_container.RunOutput;
import org.asmeta.runtime_container.SimulationContainer;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

public class zeroMQWrapper {
    
    private static final String CONFIG_FILE_PATH = "/zmq_config.properties";
    private static final String RUNTIME_MODEL_PATH = "RUNTIME_MODEL_PATH";
    private static final String ZMQ_PUB_SOCKET = "ZMQ_PUB_SOCKET";
    private static final String ZMQ_SUB_SOCKET = "ZMQ_SUB_SOCKET";
    private static final String ZMQ_REQUIRED_VARS = "ZMQ_REQUIRED_VARS";

    private SimulationContainer sim;
    private int asmId = -1;
    private ZMQ.Socket publisher;
    private ZMQ.Socket subscriber;
    private Properties properties;

    private Set<String> requiredVars;
    private Map<String, String> currentMonitoredValues;
    private Gson gson;
    private Type mapStringStringType;
    
    public zeroMQWrapper() {
        this.requiredVars = new HashSet<>();
        this.gson = new Gson();
        this.currentMonitoredValues = new HashMap<>();
        this.mapStringStringType = new TypeToken<Map<String,String>>(){}.getType();
    }

    private Properties loadConfig() throws IOException, NullPointerException {
        properties = new Properties();

        // Load config file try_catch block
        try (InputStream input = zeroMQWrapper.class.getResourceAsStream(CONFIG_FILE_PATH)) {
            if (input == null) {
                throw new IOException("Input File not found, path: " + RUNTIME_MODEL_PATH );
            }
            properties.load(input);
            
            // Retrieve properties
            // modelPath = properties.getProperty(RUNTIME_MODEL_PATH);
            // pubAddress = properties.getProperty(ZMQ_PUB_SOCKET);
            // subAddress = properties.getProperty(ZMQ_SUB_SOCKET);
            
            if(properties.getProperty(RUNTIME_MODEL_PATH) == null || properties.getProperty(ZMQ_PUB_SOCKET) == null || properties.getProperty(ZMQ_SUB_SOCKET) == null || properties.getProperty(ZMQ_REQUIRED_VARS) == null){
                // System.err.println("[zmqWrapper] ERROR Some parameters are missing, closing...");
                throw new NullPointerException("ERROR Some parameters are missing, closing...");
            }



            this.requiredVars = Arrays.stream(properties.getProperty(ZMQ_REQUIRED_VARS).split(","))
                                        .map(String::trim)
                                        .filter(s -> !s.isEmpty())
                                        .collect(Collectors.toSet());

            if (this.requiredVars.isEmpty()){
                System.err.println("[zmqWrapper] WARNING: No required vars specified in the config");
            } else {
                System.out.println("[zmqWrapper] Required monitored vars for step: " + this.requiredVars);
            }
            
        } catch (IOException e) {
            System.err.println("[zmqWrapper] ERROR loading config file '" + CONFIG_FILE_PATH + "' with error" + e.getMessage());
        }

        System.out.println("Configuration Loaded Successfully!");
        return properties;
    }

    private int initializeAsm(String modelPath) throws Exception {
        sim = new SimulationContainer();
        sim.init(1);
        asmId = sim.startExecution(modelPath);
        if (asmId < 0) {
            throw new Exception("Starting ASM model failed: negative id received ( " + asmId + " )");
        }
        System.out.println("[zmqWrapper] Started ASM Model successfully! Model path: " + modelPath + "with ID: " + asmId);
        return asmId;
    }

    private void initializeZmqSockets(ZContext context, String pubAddress, String subAddress) {
        publisher = context.createSocket(SocketType.PUB);
        publisher.bind(pubAddress);
        System.out.println("[zmqWrapper] Binded to PUB Address: " + pubAddress );
        
        subscriber = context.createSocket(SocketType.SUB);
        subscriber.bind(subAddress);
        subscriber.subscribe("".getBytes(ZMQ.CHARSET));
        System.out.println("[zmqWrapper] Binded to SUB Address: " + subAddress );


        System.out.println("[zmqWrapper] Socket initialization completed, starting loop");
    }

    // Handle data received -> updates input variables (monitored)
    private void handleSubscriptionMessages() {
        String message = subscriber.recvStr(ZMQ.DONTWAIT);
        if (message != null){
            message = message.trim();
            System.out.println("[zmqWrapper] Received: " + message);
            try {
                // Parse the message
                Map<String, String> receivedData = gson.fromJson(message, mapStringStringType);

                if (receivedData != null) {
                    currentMonitoredValues.putAll(receivedData);
                    System.err.println("[zmqWrapper] Updated monitored values, currently: " + currentMonitoredValues);
                } else {
                    System.err.println("[zmqWrapper] ERROR: Received data is null when parsed" );
                }
            } catch (Exception e) {
                System.err.println("[zmqWrapper] ERROR: Exception " + e.getMessage() );
            }
        }
    }

    // Checks if variables are ready to be sent (all required vars are present)
    private boolean areAllVarsReady() {
        if (requiredVars.isEmpty()) {
            return true;
        }
        return currentMonitoredValues.keySet().containsAll(requiredVars);
    }

    // TODO: change from output get controlled to get ouput
    private void handlePublisherMessages(RunOutput output, Map<String, String> monitoredForStep) {
        Map<String, Object> response = new HashMap<>();
        response.put("id", asmId);
        response.put("monitoredVariables", monitoredForStep);
        response.put("controlledValues", output.getControlledvalues());

        String jsonResponse = gson.toJson(response);
        System.out.println("[zmqWrapper] Publishing: " + jsonResponse);
        publisher.send(jsonResponse);
    }

    public void run() {
        try {
            
            // Configuration section
            Properties config = loadConfig();
            String modelPath = config.getProperty(RUNTIME_MODEL_PATH);
            String pubAddress = config.getProperty(ZMQ_PUB_SOCKET);
            String subAddress = config.getProperty(ZMQ_SUB_SOCKET);

            // initialize ASM
            initializeAsm(modelPath);

            // Start thread configuring sockets
            try (ZContext context = new ZContext()) {
                initializeZmqSockets(context, pubAddress, subAddress);

                // Start Loop
                while (!Thread.currentThread().isInterrupted()) {

                    // handle listen section
                    handleSubscriptionMessages();

                    // if all vars are ready, put them in the monitoredForStep to proceed with the step
                    if (areAllVarsReady()) {
                        System.out.println("[zmqWrapper] All required vars have been received: proceeding doing a step");

                        Map<String, String> monitoredForStep = new HashMap<>();
                        for (String key : requiredVars) {
                            monitoredForStep.put(key, currentMonitoredValues.get(key));
                        }
                        
                        // Run a step
                        RunOutput output = sim.runStep(asmId, monitoredForStep);
                        System.out.println("[zmqWrapper] ASM step executed. Output: " + output.getControlledvalues());
                        
                        
                        // handle publish section
                        handlePublisherMessages(output, monitoredForStep);

                        currentMonitoredValues.clear();
                        System.err.println("[zmqWrapper] Cleared monitored values, waiting for the next set of values");

                    } else {

                    }
                }

            }

            
        } catch (Exception e) {

        }
    }

    public static void main(String[] args) {
        zeroMQWrapper wrapper = new zeroMQWrapper();
        wrapper.run();
    }
}
