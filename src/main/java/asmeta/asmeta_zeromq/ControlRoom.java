package asmeta.asmeta_zeromq;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.util.Properties;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;

import com.google.gson.Gson;

public class ControlRoom {
    private static final Logger logger = LogManager.getLogger(ControlRoom.class);

    private static final String CONTROLROOM_CONFIG_PATH = "/controlroom_config.properties";
    private static final String listenAddress = "SUB_ADDRESSES";
    private Gson gson;
    private Type mapStringStringType;
    private Properties properties;

    ZMQ.Socket subscriber;

    private Properties loadConfig () throws IOException, NullPointerException {
        properties = new Properties();

        try (InputStream input = ControlRoom.class.getResourceAsStream(CONTROLROOM_CONFIG_PATH)){
            if ( input == null ) {
                logger.error("No load config found at {}", CONTROLROOM_CONFIG_PATH);
                throw new IOException("Config file not found at: " + CONTROLROOM_CONFIG_PATH);
            }
            properties.load(input);
            logger.debug("Config file loaded");

            if (properties.getProperty(listenAddress) == null) {
                logger.error("No listen address found");
                throw new NullPointerException("No listen address found");
            }

        } catch (IOException e) {
            logger.error("There was an error processing the configuration file: " + e.getMessage());
            throw e;
        } catch (NullPointerException e) {
            logger.error("There was an error processing the addresses connection configuration: " + e.getMessage());
            throw e;
        }

        logger.info("Configuration Loaded Successfully!");
        logger.info(" * {} = {}", listenAddress, properties.getProperty(listenAddress));
        return properties;
    }


    private void initializeSubSockets(ZContext context) {
        String subAddressesString = properties.getProperty(listenAddress);

        subscriber = context.createSocket(SocketType.SUB);

        String[] subAddresses = subAddressesString.split(",");
        for (String address : subAddresses) {
            String trimmedAddress = address.trim();
            logger.info("Trying to connect to: {}", trimmedAddress);
            if (!trimmedAddress.isEmpty()){
                try {
                    subscriber.connect(address);
                    logger.info("Connected to address '{}''", trimmedAddress);
                } catch (Exception e) {
                    logger.error("Failed to connect to address '{}'", trimmedAddress, e.getMessage());
                }
            }
        }

        subscriber.subscribe("".getBytes(ZMQ.CHARSET));
        logger.info("Connection phase terminated.");

        logger.info("ZeroMQ Socket initialization completed.");
    }

    private void handleSubscriptionMessages() {
        String message = subscriber.recvStr(ZMQ.DONTWAIT);
        if (message != null) {
            message = message.trim();
            logger.debug("A message has arrived: {}", message);

            // try {
            //     Map<String, String> receivedMessage = gson.fromJson(message, mapStringStringType);
            //     logger.info(message);

            // } catch (Exception e) {
            // }

        }
    }

    private void listen() {


        try (ZContext context = new ZContext() ) {
            initializeSubSockets(context);

            logger.info("Starting listening...");

            while(!Thread.currentThread().isInterrupted()) {
                handleSubscriptionMessages();
            }

        } catch (Exception e) {

        }
    }

    public static void main(String[] args) {
        logger.info("Control Room started");
        ControlRoom controlRoom = new ControlRoom();

        // load config
        try {
            controlRoom.loadConfig();
        } catch (IOException e) {
            logger.error("There was an error processing the configuration file: " + e.getMessage());
        } catch (NullPointerException e) {
            logger.error("There was an error processing the addresses connection configuration: " + e.getMessage());
        }

        
        // listen for messages
        controlRoom.listen();
    }
}