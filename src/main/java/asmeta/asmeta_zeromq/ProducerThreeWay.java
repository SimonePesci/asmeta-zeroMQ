package asmeta.asmeta_zeromq;

// Keep necessary imports: HashMap, Map, RunOutput, SimulationContainer, ZContext, ZMQ, Socket, Gson
import java.util.HashMap;
import java.util.Map;

import org.asmeta.runtime_container.RunOutput;
import org.asmeta.runtime_container.SimulationContainer;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import org.zeromq.ZMQ.Poller;
import org.zeromq.ZMQ.Socket;

import com.google.gson.Gson;


public class ProducerThreeWay {

    public static void main(String[] args) {
        SimulationContainer sim = new SimulationContainer();
        sim.init(10);

        String modelPath = "src/main/resources/models/producer.asm";
        int asmId = sim.startExecution(modelPath);
        System.out.println("[ProducerApp] Started ASM model '" + modelPath + "' with ID: " + asmId);

        Gson gson = new Gson();
        

        try (ZContext context = new ZContext()) {
            // Socket to publish results to Buffer
            Socket publisher = context.createSocket(ZMQ.PUB);
            publisher.bind("tcp://*:5556"); // Buffer listens here

            // Socket to receive initial external trigger from test
            Socket triggerSub = context.createSocket(ZMQ.SUB);
            triggerSub.bind("tcp://*:5555");
            triggerSub.subscribe("".getBytes());

            // --- Create Poller ---
            Poller items = context.createPoller(1);
            items.register(triggerSub, Poller.POLLIN); // Register triggerSub at index 0

            System.out.println("[ProducerApp] Listening for triggers on 5555, Publishing to Buffer on 5556");

            try {
                System.out.println("[ProducerApp] Allowing time for connections...");
                Thread.sleep(1000); // Initial delay for connections
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.err.println("[ProducerApp] Sleep interrupted.");
                return;
            }

            String currentTriggerValue = "0"; // Default to 0 (IDLE)

            while (!Thread.currentThread().isInterrupted()) {
                // Wait for events on registered sockets for up to 2000ms (2 seconds)
                int rc = items.poll(2000);
                if (rc == -1) {
                    System.err.println("[ProducerApp] Poller error or interrupted.");
                    break; // Exit loop on error
                }

                // Check if triggerSub (index 0) has received data
                if (items.pollin(0)) {
                    String triggerMessage = triggerSub.recvStr(ZMQ.DONTWAIT); // Use DONTWAIT as pollin guarantees data
                    if (triggerMessage != null) {
                         triggerMessage = triggerMessage.trim();
                         System.out.println("[ProducerApp] Received trigger: " + triggerMessage);
                         try {
                             @SuppressWarnings("unchecked")
                             Map<String, String> triggerMsg = gson.fromJson(triggerMessage, Map.class);
                             if (triggerMsg != null && triggerMsg.containsKey("trigger")) {
                                 currentTriggerValue = triggerMsg.get("trigger");
                                 System.out.println("[ProducerApp] Updated currentTriggerValue from triggerSub: " + currentTriggerValue);
                             } else {
                                 System.err.println("[ProducerApp] Received invalid trigger message format: " + triggerMessage);
                             }
                         } catch (Exception e) {
                              System.err.println("[ProducerApp] Error parsing trigger message: " + triggerMessage + " | Error: " + e.getMessage());
                         }
                    }
                }
                // If pollin(0) is false, it means the poll timed out (2 seconds passed)
                // without a new trigger message. We proceed using the existing currentTriggerValue.

                // --- Simulation Step (runs every loop iteration, after poll check/timeout) ---
                Map<String, String> monitored = new HashMap<>();
                monitored.put("trigger", currentTriggerValue); // Use the latest trigger value

                System.out.println("[ProducerApp] Running step with monitored: " + monitored);
                RunOutput output = sim.runStep(asmId, monitored);

                // --- Publish results ---
                Map<String, Object> response = new HashMap<>();
                response.put("id", asmId);
                response.put("source", "Producer"); // Identify source
                response.put("monitoredVariables", monitored);
                response.put("controlledValues", output.getControlledvalues());

                String json = gson.toJson(response);
                System.out.println("[ProducerApp] Sending to Buffer: " + json);
                publisher.send(json);
            }
        } catch (Exception e) {
            System.err.println("[ProducerApp] An unexpected error occurred:");
            e.printStackTrace();
        } finally {
            System.out.println("[ProducerApp] Exiting.");
            // SimulationContainer cleanup might be needed here if it holds resources
            // sim.destroy(); // Or similar method if available
        }
    }
}