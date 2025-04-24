package asmeta.asmeta_zeromq;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.asmeta.runtime_container.Esit;
import org.asmeta.runtime_container.RunOutput;
import org.asmeta.runtime_container.SimulationContainer;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

public class zeroMQWrapperFinal {

    // Add a static logger instance
    private static final Logger logger = LogManager.getLogger(zeroMQWrapperFinal.class);

    private static final String CONFIG_FILE_PATH = "/zmq_config.properties";
    private static final String RUNTIME_MODEL_PATH = "RUNTIME_MODEL_PATH";
    private static final String ZMQ_PUB_SOCKET = "ZMQ_PUB_SOCKET";
    private static final String ZMQ_SUB_CONNECT_ADDRESSES = "ZMQ_SUB_CONNECT_ADDRESSES";

    private SimulationContainer sim;
    private ZMQ.Socket publisher;
    private ZMQ.Socket subscriber;
    private Properties properties;

    private Set<String> requiredMonitored;
    private Map<String, String> currentMonitoredValues;
    private Gson gson;
    private Type mapStringStringType;

    public zeroMQWrapperFinal() {
        this.requiredMonitored = new HashSet<>();
        this.gson = new Gson();
        this.currentMonitoredValues = new HashMap<>();
        this.mapStringStringType = new TypeToken<Map<String,String>>(){}.getType();
        logger.info("zeroMQWrapper initialized.");
    }

    private Properties loadConfig() throws IOException, NullPointerException {
        properties = new Properties();

        // Load config file try_catch block
        try (InputStream input = zeroMQWrapperFinal.class.getResourceAsStream(CONFIG_FILE_PATH)) {
            if (input == null) {
                logger.error("Config file not found at path: {}", CONFIG_FILE_PATH);
                throw new IOException("Config file not found, path: " + CONFIG_FILE_PATH );
            }
            properties.load(input);
            logger.debug("Properties loaded from file.");

            // Check for essential properties
            if(properties.getProperty(RUNTIME_MODEL_PATH) == null || properties.getProperty(ZMQ_PUB_SOCKET) == null
            //  || properties.getProperty(ZMQ_SUB_CONNECT_ADDRESSES) == null
             ){
                String missing = "";
                if (properties.getProperty(RUNTIME_MODEL_PATH) == null) missing += RUNTIME_MODEL_PATH + " ";
                if (properties.getProperty(ZMQ_PUB_SOCKET) == null) missing += ZMQ_PUB_SOCKET + " ";
                // if (properties.getProperty(ZMQ_SUB_CONNECT_ADDRESSES) == null) missing += ZMQ_SUB_CONNECT_ADDRESSES + " ";
                logger.error("Essential configuration parameters missing: {}", missing.trim());
                throw new NullPointerException("ERROR Essential parameters missing: " + missing.trim() + ", closing...");
            }

        } catch (IOException e) {
            logger.error("ERROR loading config file '{}': {}", CONFIG_FILE_PATH, e.getMessage(), e);
            throw e;
        } catch (NullPointerException e) {
             logger.error("ERROR checking config properties: {}", e.getMessage(), e);
             throw e;
        }

        logger.info("Configuration Loaded Successfully!");
        logger.info(" * {} = {}", RUNTIME_MODEL_PATH, properties.getProperty(RUNTIME_MODEL_PATH));
        logger.info(" * {} = {}", ZMQ_PUB_SOCKET, properties.getProperty(ZMQ_PUB_SOCKET));
        logger.info(" * {} = {}", ZMQ_SUB_CONNECT_ADDRESSES, properties.getProperty(ZMQ_SUB_CONNECT_ADDRESSES));
        return properties;
    }

    private int initializeAsm(String modelPath) throws Exception {
        logger.info("Initializing ASM simulation container...");
        sim = new SimulationContainer();
        sim.init(1);
        logger.debug("Simulation container initialized.");

        logger.info("Starting ASM execution for model: {}", modelPath);
        int asmId = sim.startExecution(modelPath);

        if (asmId < 0) {
            logger.error("Starting ASM model failed: negative id received ({})", asmId);
            throw new Exception("Starting ASM model failed: negative id received ( " + asmId + " )");
        }
        logger.info("Started ASM Model successfully! Model path: {} with ID: {}", modelPath, asmId);

        // Load monitored from model directly
        this.requiredMonitored.addAll(sim.getMonitored(modelPath));

        if (this.requiredMonitored.isEmpty()){
            logger.warn("No required monitored vars specified in the model '{}'", modelPath);
        } else {
            logger.info("Required monitored vars for model ID {}: {}", asmId, this.requiredMonitored);
        }

        return asmId;
    }

    private void initializeZmqSockets(ZContext context, String pubBindAddress, String subConnectAddressesString) {
        logger.info("Initializing ZeroMQ sockets...");

        publisher = context.createSocket(SocketType.PUB);
        publisher.bind(pubBindAddress);
        logger.info("PUB Socket bound to Address: {}", pubBindAddress);

        subscriber = context.createSocket(SocketType.SUB);
        
        String[] subAddresses = subConnectAddressesString.split(",");
        logger.info("Attempting to connect SUB socket to {} address(es)...", subAddresses.length);
        for (String addr : subAddresses) {
            String trimmedAddr = addr.trim();
            if(!trimmedAddr.isEmpty()) {
                try {
                    subscriber.connect(trimmedAddr);
                    logger.info("Trying to connect to address {}...", trimmedAddr);
                } catch (Exception e) {
                    logger.error("Failed to connect to address '{}'': {}", trimmedAddr, e.getMessage());
                }
            }
        }

        subscriber.subscribe("".getBytes(ZMQ.CHARSET));
        logger.info("Connection phase terminated.");

        logger.info("ZeroMQ Socket initialization completed.");
    }

    // Handle data received -> updates input variables (monitored)
    private void handleSubscriptionMessages() {
        String message = subscriber.recvStr(ZMQ.DONTWAIT);
        if (message != null){
            message = message.trim();
            logger.debug("Received message on SUB socket: {}", message);
            try {
                // Parse the message
                Map<String, String> receivedData = gson.fromJson(message, mapStringStringType);

                if (receivedData != null) {
                    // Update current values, overwriting existing keys if present
                    currentMonitoredValues.putAll(receivedData);
                    logger.debug("Updated monitored values map, currently contains keys: {}", currentMonitoredValues.keySet());
                } else {
                    logger.warn("Parsed JSON message resulted in null data object. Original message: '{}'", message);
                }
            } catch (com.google.gson.JsonSyntaxException e) {
                logger.error("ERROR: Failed to parse received message as JSON: {}. Message: '{}'", e.getMessage(), message);
            } catch (Exception e) {
                // Catch other potential exceptions during parsing/update
                logger.error("ERROR: Exception processing received message: {}", e.getMessage(), e);
            }
        }
        // No message received, just continue the loop
    }

    // Checks if variables are ready to be sent (all required vars are present)
    private boolean areAllVarsReady() {
        if (requiredMonitored.isEmpty()) {
            // If no variables are monitored, we are always ready to step
            return true;
        }
        // Check if the keys in the current values contain all required keys
        boolean ready = currentMonitoredValues.keySet().containsAll(requiredMonitored);
        if (!ready) {
             logger.trace("Waiting for required monitored vars. Have: {}, Need: {}", currentMonitoredValues.keySet(), requiredMonitored);
        }
        return ready;
    }

    // Prepare and publish the output from the ASM step
    private void handlePublisherMessages(RunOutput output) {
        Map<String, Object> response = new HashMap<>();    
        response.putAll(output.getOutvalues());
        response.put("asm_status", output.getEsit().toString());

        String jsonResponse = gson.toJson(response);
        logger.info("Publishing output: {}", jsonResponse);

        publisher.send(jsonResponse);
    }

    private void handleUnsafeState(Map<String, String> monitoredForStep) {
        logger.error("ASM state is UNSAFE after step with input: {}", monitoredForStep);
    }


    public void run() {
        try {
            logger.info("Starting zeroMQWrapper run loop...");
            // Configuration section
            Properties config = loadConfig();
            String modelPath = config.getProperty(RUNTIME_MODEL_PATH);
            String pubAddress = config.getProperty(ZMQ_PUB_SOCKET);
            String subConnectAddresses = config.getProperty(ZMQ_SUB_CONNECT_ADDRESSES);

            // initialize ASM
            int asmId = initializeAsm(modelPath);

            try (ZContext context = new ZContext()) {
                initializeZmqSockets(context, pubAddress, subConnectAddresses);

                logger.info("Entering main loop...");
                // Start Loop
                while (!Thread.currentThread().isInterrupted()) {

                    // 1. Handle listen section 
                    handleSubscriptionMessages();

                    // 2. Check if ready for a step
                    if (areAllVarsReady()) {
                        logger.info("All required monitored vars received ({}), proceeding with ASM step.", requiredMonitored);

                        // Prepare input for this step
                        Map<String, String> monitoredForStep = new HashMap<>();
                        for (String key : requiredMonitored) {
                            monitoredForStep.put(key, currentMonitoredValues.get(key));
                        }
                        logger.debug("Executing ASM step with monitored input: {}", monitoredForStep);

                        // 3. Run a step
                        RunOutput output = sim.runStep(asmId, monitoredForStep);

                        // 4. Process result and publish
                        if (output.getEsit() == Esit.SAFE){
                            logger.info("ASM step completed successfully (SAFE). Output: {}", output.getOutvalues());
                            handlePublisherMessages(output);
                        } else {
                            handleUnsafeState(monitoredForStep);                        
                        }

                        logger.debug("Step processing complete. Current monitored keys: {}", currentMonitoredValues.keySet());

                    } else {
                        // Not all variables are ready, wait briefly before checking again
                        try {
                            Thread.sleep(50); // Sleep for 50 milliseconds
                        } catch (InterruptedException e) {
                            logger.warn("Main loop sleep interrupted.");
                            Thread.currentThread().interrupt(); // Re-interrupt the thread
                        }
                    }
                } // End while loop

                logger.info("Main loop interrupted. Shutting down.");

            } // ZContext automatically closed here

        } catch (IOException | NullPointerException configEx) {
            logger.fatal("Failed to load or validate configuration: {}", configEx.getMessage());
        } catch (Exception e) {
            logger.fatal("CRITICAL ERROR in run loop: {}", e.getMessage(), e);
            
        } finally {
            logger.info("zeroMQWrapper run method finished.");
        }
    }

    public static void main(String[] args) {
        logger.info("Starting zeroMQWrapper application...");
        zeroMQWrapperFinal wrapper = new zeroMQWrapperFinal();
        wrapper.run();
        logger.info("zeroMQWrapper application finished.");
    }
}