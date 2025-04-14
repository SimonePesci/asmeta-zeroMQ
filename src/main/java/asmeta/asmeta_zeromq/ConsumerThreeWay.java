package asmeta.asmeta_zeromq;

// Keep necessary imports: HashMap, Map, RunOutput, SimulationContainer, ZContext, ZMQ, Socket, Gson, Type, TypeToken
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


public class ConsumerThreeWay {

    public static void main(String[] args) {
        SimulationContainer sim = new SimulationContainer();
        sim.init(10);

        String modelPath = "src/main/resources/models/consumer.asm";
        int asmId = sim.startExecution(modelPath);
        System.out.println("[ConsumerApp] Started ASM model '" + modelPath + "' with ID: " + asmId);

        Gson gson = new Gson();
        // Type for parsing messages received from Buffer
        Type bufferMsgType = new TypeToken<Map<String, Object>>(){}.getType();

        try (ZContext context = new ZContext()) {
            

            // Socket to receive messages from Buffer
            Socket subscriber = context.createSocket(ZMQ.SUB);
            subscriber.connect("tcp://localhost:5557"); // Connect to Buffer's publish port
            subscriber.subscribe("".getBytes());

            System.out.println("[ConsumerApp] Listening for Buffer on 5557");

            try {
                System.out.println("[ConsumerApp] Allowing time for connections...");
                Thread.sleep(1000); // Keep delay
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                 System.err.println("[ConsumerApp] Sleep interrupted.");
                return;
            }

            while (!Thread.currentThread().isInterrupted()) {
                // Wait for message from Buffer (blocking)
                String message = subscriber.recvStr(0);
                if (message == null) {
                    System.err.println("[ConsumerApp] Received null message from Buffer, exiting loop.");
                    break;
                }
                message = message.trim();
                System.out.println("[ConsumerApp] Received from Buffer: " + message);

                Map<String, String> consumerMonitored = new HashMap<>();
                try {
                    // Parse message: {"id":..., "source":"Buffer", "controlledValues":{"outgoingStatus":"HELLO_WORLD"}, ...}
                    Map<String, Object> received = gson.fromJson(message, bufferMsgType);

                    if (received != null && received.containsKey("controlledValues")) {
                        @SuppressWarnings("unchecked")
                        Map<String, String> bufferControlled = (Map<String, String>) received.get("controlledValues");
                        // Expect 'outgoingStatus' from buffer.asm
                        if (bufferControlled != null && bufferControlled.containsKey("outgoingStatus")) {
                            // Map Buffer's 'outgoingStatus' to Consumer's monitored 'incomingStatus'
                            consumerMonitored.put("incomingStatus", bufferControlled.get("outgoingStatus"));
                        } else {
                            System.err.println("[ConsumerApp] Buffer message missing 'outgoingStatus' in 'controlledValues': " + message);
                            continue; // Skip this message
                        }
                    } else {
                        System.err.println("[ConsumerApp] Buffer message missing 'controlledValues': " + message);
                        continue; // Skip this message
                    }
                } catch (Exception e) {
                    System.err.println("[ConsumerApp] Error parsing Buffer message: " + message + " | Error: " + e.getMessage());
                    continue; // Skip this message
                }

                // Run the Consumer simulation step
                System.out.println("[ConsumerApp] Running step with monitored: " + consumerMonitored);
                RunOutput output = sim.runStep(asmId, consumerMonitored);

                // Log the result (Consumer no longer publishes)
                System.out.println("[ConsumerApp] Step completed. Controlled values: " + output.getControlledvalues());

                // Remove sending response back
            }
        } catch (Exception e) {
             System.err.println("[ConsumerApp] An unexpected error occurred:");
             e.printStackTrace();
        } finally {
             System.out.println("[ConsumerApp] Exiting.");
        }
    }
}