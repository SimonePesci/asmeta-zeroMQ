package asmeta.asmeta_zeromq;

import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;

import org.asmeta.runtime_container.RunOutput;
import org.asmeta.runtime_container.SimulationContainer;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import org.zeromq.ZMQ.Poller;
import org.zeromq.ZMQ.Socket;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

public class ProducerApp {

    public static void main(String[] args) {
        SimulationContainer sim = new SimulationContainer();
        sim.init(10);

        String modelPath = "src/main/resources/models/producer.asm";
        int asmId = sim.startExecution(modelPath);
        System.out.println("[ProducerApp] Started ASM model '" + modelPath + "' with ID: " + asmId);

        Gson gson = new Gson();
        Type mapStringObjectType = new TypeToken<Map<String, Object>>(){}.getType();

        try (ZContext context = new ZContext()) {
            Socket publisher = context.createSocket(ZMQ.PUB);
            publisher.bind("tcp://*:5556");

            Socket triggerSub = context.createSocket(ZMQ.SUB);
            triggerSub.bind("tcp://*:5555");
            triggerSub.subscribe("".getBytes());

            Socket consumerSub = context.createSocket(ZMQ.SUB);
            consumerSub.connect("tcp://localhost:5557");
            consumerSub.subscribe("".getBytes());

            System.out.println("[ProducerApp] Ready. Waiting for initial trigger on 5555...");

            
            Poller items = context.createPoller(2);
            items.register(triggerSub, Poller.POLLIN); // Index 0
            items.register(consumerSub, Poller.POLLIN); // Index 1

            boolean initialTriggerReceived = false;
            String currentTriggerValue = "0"; // Start with default

            while (!Thread.currentThread().isInterrupted()) {

                // Poll indefinitely until a message arrives on *any* registered socket
                int rc = items.poll(-1); // Blocking poll
                if (rc == -1) {
                    System.err.println("[ProducerApp] Poller interrupted or error.");
                    break;
                }

                // --- State 1: Waiting for Initial Trigger ---
                if (!initialTriggerReceived) {
                    if (items.pollin(0)) { // Check triggerSub (index 0)
                        String message = triggerSub.recvStr(ZMQ.DONTWAIT);
                        if (message != null) {
                            message = message.trim();
                            System.out.println("[ProducerApp] Received initial trigger: " + message);
                            try {
                                @SuppressWarnings("unchecked")
                                Map<String, String> triggerMsg = gson.fromJson(message, Map.class);
                                if (triggerMsg != null && triggerMsg.containsKey("trigger")) {
                                    currentTriggerValue = triggerMsg.get("trigger"); // Should be "1"
                                    System.out.println("[ProducerApp] Initial trigger processed. currentTriggerValue: " + currentTriggerValue);

                                    // --- Run step and send FIRST message ---
                                    Map<String, String> monitored = new HashMap<>();
                                    monitored.put("trigger", currentTriggerValue);
                                    System.out.println("[ProducerApp] Running initial step with monitored: " + monitored);
                                    RunOutput output = sim.runStep(asmId, monitored);

                                    Map<String, Object> response = new HashMap<>();
                                    response.put("id", asmId);
                                    response.put("monitoredVariables", monitored);
                                    response.put("controlledValues", output.getControlledvalues());
                                    String json = gson.toJson(response);
                                    System.out.println("[ProducerApp] Sending initial state to Consumer: " + json);
                                    publisher.send(json);
                                    // --- End of first action ---

                                    initialTriggerReceived = true; // Transition to next state
                                    System.out.println("[ProducerApp] Now waiting only for Consumer responses on 5557...");

                                } else {
                                    System.err.println("[ProducerApp] Received invalid initial trigger format: " + message);
                                    // Stay in initial state, wait for a valid trigger
                                }
                            } catch (Exception e) {
                                System.err.println("[ProducerApp] Error parsing initial trigger: " + message + " | Error: " + e.getMessage());
                                // Stay in initial state
                            }
                        }
                    }
                    // Ignore consumerSub messages (items.pollin(1)) before initial trigger
                }
                // --- State 2: Waiting for Consumer Response ---
                else { // initialTriggerReceived is true
                    if (items.pollin(1)) { // Check consumerSub (index 1)
                        String message = consumerSub.recvStr(ZMQ.DONTWAIT);
                        if (message != null) {
                            message = message.trim();
                            System.out.println("[ProducerApp] Received from Consumer: " + message);
                            try {
                                // Parse consumer message to decide next state
                                Map<String, Object> received = gson.fromJson(message, mapStringObjectType);
                                if (received != null && received.containsKey("controlledValues")) {
                                    @SuppressWarnings("unchecked")
                                    Map<String, String> controlled = (Map<String, String>) received.get("controlledValues");
                                    if (controlled != null && controlled.containsKey("state")) {
                                        String consumerState = controlled.get("state");
                                        // map Consumer state back to Producer trigger
                                        currentTriggerValue = "RESPONDED".equals(consumerState) ? "1" : "0"; 
                                        System.out.println("[ProducerApp] Mapped consumer state '" + consumerState + "' to currentTriggerValue: " + currentTriggerValue);

                                        // --- Run step and send SUBSEQUENT message ---
                                        Map<String, String> monitored = new HashMap<>();
                                        monitored.put("trigger", currentTriggerValue);
                                        System.out.println("[ProducerApp] Running step based on consumer response with monitored: " + monitored);
                                        RunOutput output = sim.runStep(asmId, monitored);

                                        Map<String, Object> response = new HashMap<>();
                                        response.put("id", asmId);
                                        response.put("monitoredVariables", monitored);
                                        response.put("controlledValues", output.getControlledvalues());
                                        String json = gson.toJson(response);
                                        System.out.println("[ProducerApp] Sending updated state to Consumer: " + json);
                                        publisher.send(json);
                                        // --- End of subsequent action ---

                                    } else {
                                         System.err.println("[ProducerApp] Received invalid message format from consumer (missing state): " + message);
                                    }
                                } else {
                                     System.err.println("[ProducerApp] Received invalid message format from consumer (missing controlledValues): " + message);
                                }
                            } catch (Exception e) {
                                 System.err.println("[ProducerApp] Error parsing consumer message: " + message + " | Error: " + e.getMessage());
                            }
                        }
                    }
                    
                }
            } // End of while loop

        } catch (Exception e) {
            System.err.println("[ProducerApp] An unexpected error occurred:");
            e.printStackTrace();
        } finally {
             System.out.println("[ProducerApp] Exiting.");
        }
    }
}
