package asmeta.asmeta_zeromq;

import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

import org.asmeta.runtime_container.RunOutput;
import org.asmeta.runtime_container.SimulationContainer;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import org.zeromq.ZMQ.Socket;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken; // Import TypeToken

public class ConsumerApp {

    public static void main(String[] args) {
        SimulationContainer sim = new SimulationContainer();
        sim.init(10);

        String modelPath = "src/main/resources/models/consumer.asm";
        int asmId = sim.startExecution(modelPath);
        System.out.println("[ConsumerApp] Started ASM model '" + modelPath + "' with ID: " + asmId);

        Gson gson = new Gson();
        // Type for parsing messages received from Producer
        Type producerMsgType = new TypeToken<Map<String, Object>>(){}.getType();

        try (ZContext context = new ZContext()) {
            // Socket to publish results back to Producer
            Socket publisher = context.createSocket(ZMQ.PUB);
            publisher.bind("tcp://*:5557"); // publish where Producer listens

            // Socket to receive messages from Producer
            Socket subscriber = context.createSocket(ZMQ.SUB);
            subscriber.connect("tcp://localhost:5556"); // connect to Producer's publish port
            subscriber.subscribe("".getBytes());

            System.out.println("[ConsumerApp] Listening on 5556, Publishing on 5557");

            try {
                System.out.println("[ConsumerApp] Allowing time for connections...");
                Thread.sleep(1000); // Keep delay
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                 System.err.println("[ConsumerApp] Sleep interrupted.");
                return;
            }

            while (!Thread.currentThread().isInterrupted()) {
                // Wait for message from Producer (blocking)
                String message = subscriber.recvStr(0);
                if (message == null) {
                    System.err.println("[ConsumerApp] Received null message from Producer, exiting loop.");
                    break;
                }
                message = message.trim();
                System.out.println("[ConsumerApp] Received from Producer: " + message);

                Map<String, String> consumerMonitored = new HashMap<>();
                try {
                    // Parse message from Producer
                    Map<String, Object> received = gson.fromJson(message, producerMsgType);

                    if (received != null && received.containsKey("controlledValues")) {
                        @SuppressWarnings("unchecked")
                        Map<String, String> producerControlled = (Map<String, String>) received.get("controlledValues");
                        // Expect 'status' from producer.asm
                        if (producerControlled != null && producerControlled.containsKey("status")) {
                            // Map Producer's 'status' to Consumer's monitored 'incomingStatus'
                            consumerMonitored.put("incomingStatus", producerControlled.get("status"));
                        } else {
                            System.err.println("[ConsumerApp] Producer message missing 'status' in 'controlledValues': " + message);
                            continue; // Skip this message
                        }
                    } else {
                        System.err.println("[ConsumerApp] Producer message missing 'controlledValues': " + message);
                        continue; // Skip this message
                    }
                } catch (Exception e) {
                    System.err.println("[ConsumerApp] Error parsing Producer message: " + message + " | Error: " + e.getMessage());
                    continue; // Skip this message
                }

                // Run the Consumer simulation step
                System.out.println("[ConsumerApp] Running step with monitored: " + consumerMonitored);
                RunOutput output = sim.runStep(asmId, consumerMonitored);

                // Prepare response to send back to Producer
                Map<String, Object> response = new HashMap<>();
                response.put("id", asmId); // Consumer's ASM id
                response.put("source", "Consumer"); // Identify source
                response.put("monitoredVariables", consumerMonitored); // Include what was monitored
                response.put("controlledValues", output.getControlledvalues()); // Include consumer's state

                String json = gson.toJson(response);
                System.out.println("[ConsumerApp] Sending to Producer: " + json);

                // Add small delay before sending
                try { Thread.sleep(50); } catch (InterruptedException e) { Thread.currentThread().interrupt(); break;}
                publisher.send(json); // Send the response
            }
        } catch (Exception e) {
             System.err.println("[ConsumerApp] An unexpected error occurred:");
             e.printStackTrace();
        } finally {
             System.out.println("[ConsumerApp] Exiting.");
        }
    }
}

// mvn exec:java -Dexec.mainClass=asmeta.asmeta_zeromq.ConsumerApp
// mvn exec:java -Dexec.mainClass=asmeta.asmeta_zeromq.ProducerApp