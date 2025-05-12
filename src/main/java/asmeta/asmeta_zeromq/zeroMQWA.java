package asmeta.asmeta_zeromq;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
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

public class zeroMQWA {

    // Add a static logger instance
    private static final Logger logger = LogManager.getLogger(zeroMQWA.class);

    private final String CONFIG_FILE_PATH;
    private static final String RUNTIME_MODEL_PATH = "RUNTIME_MODEL_PATH";
    private static final String ZMQ_PUB_SOCKET = "ZMQ_PUB_SOCKET";
    private static final String ZMQ_SUB_CONNECT_ADDRESSES = "ZMQ_SUB_CONNECT_ADDRESSES";

    private SimulationContainer sim;
    private ZContext context;
    private ZMQ.Socket publisher;
    private final List<ZMQ.Socket> subscribers = new ArrayList<>();
    private Properties properties;

    // Add instance variables to store config and asmId
    private int asmId;
    private String modelPath;
    private String pubAddress;
    private String subConnectAddresses;

    private Set<String> requiredMonitored;
    private Map<String, String> currentMonitoredValues;
    private Gson gson;
    private Type mapStringStringType;

    public zeroMQWA(String config_filepath) {
        this.requiredMonitored = new HashSet<>();
        this.gson = new Gson();
        this.currentMonitoredValues = new HashMap<>();
        this.mapStringStringType = new TypeToken<Map<String,String>>(){}.getType();
        this.CONFIG_FILE_PATH = config_filepath;
        logger.info("zeroMQW initialized for config: {}", config_filepath);

        try {
            // Configuration section
            Properties config = this.loadConfig();
            this.modelPath = config.getProperty(RUNTIME_MODEL_PATH);
            this.pubAddress = config.getProperty(ZMQ_PUB_SOCKET);
            this.subConnectAddresses = config.getProperty(ZMQ_SUB_CONNECT_ADDRESSES, "");

            // Initialize ASM
            this.asmId = this.initializeAsm(this.modelPath);

            // Initialize ZMQ context and sockets
            this.initializeZmq();

            logger.info("zeroMQW instance configured successfully for {}", config_filepath);

        } catch (IOException | NullPointerException e) {
            logger.fatal("CRITICAL ERROR during zeroMQW initialization for {}: {}", config_filepath, e.getMessage(), e);
        } catch (Exception e) {
             logger.fatal("CRITICAL ERROR during initialization for {}: {}", config_filepath, e.getMessage(), e);
        }
    }

    private Properties loadConfig() throws IOException, NullPointerException {
        properties = new Properties();
        try (InputStream input = zeroMQW.class.getResourceAsStream(CONFIG_FILE_PATH)) {
            if (input == null) {
                logger.error("Config file not found at path: {}", CONFIG_FILE_PATH);
                throw new IOException("Config file not found, path: " + CONFIG_FILE_PATH );
            }
            properties.load(input);
            logger.debug("Properties loaded from file.");

            if(properties.getProperty(RUNTIME_MODEL_PATH) == null || properties.getProperty(ZMQ_PUB_SOCKET) == null){
                String missing = "";
                if (properties.getProperty(RUNTIME_MODEL_PATH) == null) missing += RUNTIME_MODEL_PATH + " ";
                if (properties.getProperty(ZMQ_PUB_SOCKET) == null) missing += ZMQ_PUB_SOCKET + " ";
                logger.error("Essential configuration parameters missing: {}", missing.trim());
                throw new NullPointerException("ERROR Essential parameters missing: " + missing.trim());
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
        this.requiredMonitored.addAll(sim.getMonitored(modelPath));
        if (this.requiredMonitored.isEmpty()){
            logger.warn("No required monitored vars specified in the model '{}'", modelPath);
        } else {
            logger.info("Required monitored vars for model ID {}: {}", asmId, this.requiredMonitored);
        }
        return asmId;
    }

    private void initializeZmq() {
        logger.info("Initializing ZeroMQ context and sockets...");
        this.context = new ZContext();
        initializeZmqSockets(this.context, this.pubAddress, this.subConnectAddresses);
        logger.info("ZeroMQ context and sockets initialized.");
    }

    private void initializeZmqSockets(ZContext context, String pubBindAddress, String subConnectAddressesString) {
        logger.info("Initializing ZeroMQ sockets...");
        publisher = context.createSocket(SocketType.PUB);
        publisher.bind(pubBindAddress);
        logger.info("PUB Socket bound to Address: {}", pubBindAddress);

        String[] subAddresses = subConnectAddressesString.split(",");
        logger.info("Attempting to create and connect {} SUB socket(s)...", subAddresses.length);
        subscribers.clear();
        for (String addr : subAddresses) {
            String trimmedAddr = addr.trim();
            if(!trimmedAddr.isEmpty()) {
                try {
                    ZMQ.Socket sub = context.createSocket(SocketType.SUB);
                    sub.connect(trimmedAddr);
                    sub.subscribe("".getBytes(ZMQ.CHARSET));
                    subscribers.add(sub);
                    logger.info("SUB socket connected and subscribed to address {}", trimmedAddr);
                } catch (Exception e) {
                    logger.error("Failed to connect SUB socket to address '{}': {}", trimmedAddr, e.getMessage());
                }
            }
        }
        logger.info("ZeroMQ Socket initialization completed with {} SUB connections.", subscribers.size());
    }

    private void handleSubscriptionMessages() {
        boolean messageReceived = false;
        for (int i = 0; i < subscribers.size(); i++) {
            ZMQ.Socket sub = subscribers.get(i);
            String message;
            while ((message = sub.recvStr(ZMQ.DONTWAIT)) != null) {
                messageReceived = true;
                message = message.trim();
                logger.debug("Received message on SUB socket #{}: {}", i, message);
                try {
                    Map<String, String> receivedData = gson.fromJson(message, mapStringStringType);
                    if (receivedData != null) {
                        currentMonitoredValues.putAll(receivedData);
                        logger.trace("Monitored values updated: {}", currentMonitoredValues);
                    } else {
                        logger.warn("Parsed JSON was null from SUB socket #{}, msg='{}'", i, message);
                    }
                } catch (Exception e) {
                    logger.error("Failed to parse incoming JSON on SUB socket #{}: {}", i, e.getMessage());
                }
            }
        }
         if (!messageReceived) {
             logger.trace("No messages received on any SUB socket in this check.");
         }
    }

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
        logger.info("Starting zeroMQW run loop for {}...", CONFIG_FILE_PATH);
        try {

            Thread.sleep(1500);

            // Loop indefinitely
            while (true) {
                logger.trace("Starting new loop iteration for {}...", CONFIG_FILE_PATH);

                // 1. Handle listen section (check all subscribers)
                handleSubscriptionMessages();

                // 2. Prepare input for this step
                Map<String, String> monitoredForStep = new HashMap<>();
                for (String key : requiredMonitored) {
                    // Directly get value, might be null if not received yet
                    monitoredForStep.put(key, currentMonitoredValues.get(key));
                }
                logger.debug("Executing ASM step with monitored input: {}", monitoredForStep);

                // 3. Run a step
                RunOutput output = sim.runStep(this.asmId, monitoredForStep);

                // 4. Process result and publish
                if (output.getEsit() == Esit.SAFE){
                    logger.info("ASM step completed successfully (SAFE). Output: {}", output.getOutvalues());
                    handlePublisherMessages(output);
                } else {
                    handleUnsafeState(monitoredForStep);
                }

                logger.debug("Step processing complete for this iteration.");

            } // End while loop

        } catch (Exception e) {
             // Log critical errors that might cause the loop to terminate
            logger.fatal("CRITICAL ERROR in run loop for {}: {}", CONFIG_FILE_PATH, e.getMessage(), e);
             // Depending on requirements, you might want to attempt recovery or ensure cleanup
        } finally {
            // This part will likely not be reached in normal operation due to while(true)
            // unless an exception occurs or the thread is interrupted.
             logger.info("zeroMQW run loop finished for {}.", CONFIG_FILE_PATH);
             // Consider adding resource cleanup (closing sockets/context) here if needed,
             // although the explicit close method was removed previously.
        }
    }

    // NOTE: The ZMQ context and sockets are initialized in the constructor
    // and are not automatically closed in this setup since the close() method was removed.
    // External management or a separate shutdown hook might be needed for graceful resource release.

}