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
import com.google.gson.reflect.TypeToken;

public class BufferApp {

    public static void main(String[] args) {
        SimulationContainer sim = new SimulationContainer();
        sim.init(10); // Initialize simulation container

        String modelPath = "src/main/resources/models/buffer.asm";
        int asmId = sim.startExecution(modelPath);
        System.out.println("[BufferApp] Started ASM model '" + modelPath + "' with ID: " + asmId);

        Gson gson = new Gson();
        // Type for parsing messages received from Producer
        Type producerMsgType = new TypeToken<Map<String, Object>>(){}.getType();

        try (ZContext context = new ZContext()) {
            // Socket to receive messages from Producer
            Socket producerSub = context.createSocket(ZMQ.SUB);
            producerSub.connect("tcp://localhost:5556"); // Connect where Producer publishes
            producerSub.subscribe("".getBytes());
            System.out.println("[BufferApp] Connecting to Producer on tcp://localhost:5556");

            // Socket to publish messages to Consumer
            Socket consumerPub = context.createSocket(ZMQ.PUB);
            consumerPub.bind("tcp://*:5557"); // Publish where Consumer listens
            System.out.println("[BufferApp] Publishing to Consumer on 5557");

            // Allow time for connections
            try {
                System.out.println("[BufferApp] Allowing time for connections...");
                Thread.sleep(1000); // 1 second delay
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.err.println("[BufferApp] Sleep interrupted during setup.");
                return;
            }

            while (!Thread.currentThread().isInterrupted()) {
                // Wait for a message from the Producer (blocking)
                String message = producerSub.recvStr(0);
                if (message == null) {
                    System.err.println("[BufferApp] Received null message from Producer, exiting loop.");
                    break;
                }
                message = message.trim();
                System.out.println("[BufferApp] Received from Producer: " + message);

                Map<String, String> bufferMonitored = new HashMap<>();
                try {
                    // Parse message: {"id":..., "controlledValues":{"status":"HELLO_WORLD"}, ...}
                    Map<String, Object> received = gson.fromJson(message, producerMsgType);

                    if (received != null && received.containsKey("controlledValues")) {
                        @SuppressWarnings("unchecked")
                        Map<String, String> producerControlled = (Map<String, String>) received.get("controlledValues");
                        if (producerControlled != null && producerControlled.containsKey("status")) {
                            // Map Producer's controlled 'status' to Buffer's monitored 'incomingStatus'
                            bufferMonitored.put("incomingStatus", producerControlled.get("status"));
                        } else {
                            System.err.println("[BufferApp] Producer message missing 'status' in 'controlledValues': " + message);
                            continue; // Skip this message
                        }
                    } else {
                        System.err.println("[BufferApp] Producer message missing 'controlledValues': " + message);
                        continue; // Skip this message
                    }
                } catch (Exception e) {
                    System.err.println("[BufferApp] Error parsing Producer message: " + message + " | Error: " + e.getMessage());
                    continue; // Skip this message
                }

                // Run the Buffer simulation step
                System.out.println("[BufferApp] Running step with monitored: " + bufferMonitored);
                RunOutput bufferOutput = sim.runStep(asmId, bufferMonitored);

                // Prepare message for Consumer
                Map<String, Object> response = new HashMap<>();
                response.put("id", asmId); // Buffer's ASM id
                response.put("source", "Buffer"); // Identify the source
                // Pass the Buffer's controlled values (which includes 'outgoingStatus')
                response.put("controlledValues", bufferOutput.getControlledvalues());

                String json = gson.toJson(response);
                System.out.println("[BufferApp] Sending to Consumer: " + json);

                // Add small delay before sending
                try { Thread.sleep(50); } catch (InterruptedException e) { Thread.currentThread().interrupt(); break;}
                consumerPub.send(json);
            }

        } catch (Exception e) {
            System.err.println("[BufferApp] An unexpected error occurred:");
            e.printStackTrace();
        } finally {
            System.out.println("[BufferApp] Exiting.");
        }
    }
}