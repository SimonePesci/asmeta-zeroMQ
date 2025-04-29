package asmeta.asmeta_zeromq.producerconsumer2;

import java.util.HashMap;
import java.util.Map;

import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;

import com.google.gson.Gson;

public class trigger {

    private final static  Gson gson = new Gson();
    public static void main(String[] args) {

        try (ZContext context = new ZContext()) {
            ZMQ.Socket triggerPublisher = context.createSocket(SocketType.PUB);
            // String triggerPubAddress = "tcp://*:5562";
            String triggerPubAddress = "tcp://*:5560";
            triggerPublisher.bind(triggerPubAddress);
            System.out.println("Test Trigger PUB socket bound to " + triggerPubAddress);
            
            Map<String, String> monitored = new HashMap<>();
			monitored.put("trigger", "1");
            
            
            String jsonMessage = gson.toJson(monitored);
            triggerPublisher.send(jsonMessage);
            System.out.println("Sent message" + jsonMessage);
        } catch (Exception e) {
            System.out.print("error" + e.getMessage());
        }
    }
}
