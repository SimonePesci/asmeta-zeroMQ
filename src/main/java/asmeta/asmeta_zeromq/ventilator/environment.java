package asmeta.asmeta_zeromq.ventilator;

import java.io.InputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;

import com.google.gson.Gson;

public class environment {
    private final static Gson gson = new Gson();

    private static final String ENVIRONMENT_FUNCTIONS = "env_functions";

    private static List<String> environmentFunctions;
    private static final Map<String, List<String>> environmentFunctionsValues = new java.util.HashMap<>();

    public static void main(String[] args) {
        // 1) Load bind address & pause interval
        Properties props = new Properties();
        try (InputStream in = environment.class.getClassLoader()
                                          .getResourceAsStream("ventilator/zmq_config_environment.properties")) {
            if (in == null) throw new RuntimeException("zmq_config_environment.properties not found in classpath under digitalTwinExample/");
            props.load(in);

        } catch (Exception e) {
            throw new RuntimeException("Cannot load zmq_config_environment.properties", e);
        }
        String address = props.getProperty("address");

        // 2) Load environment functions in a list
        environmentFunctions = Arrays.asList(props.getProperty(ENVIRONMENT_FUNCTIONS).split(","));
        System.out.println("Environment functions: " + environmentFunctions);

        // 3) Load environment functions values in a map referencing the function name
        int maxLength = 0;
        for (String function : environmentFunctions) {
            environmentFunctionsValues.put(function, Arrays.asList(props.getProperty(function).split(",")));
            if (environmentFunctionsValues.get(function).size() > maxLength) {
                maxLength = environmentFunctionsValues.get(function).size();
            }
        }

        for (String function : environmentFunctions) {
            System.out.println("Function: " + function + " values: " + environmentFunctionsValues.get(function));
        }

        // 4) Load pause interval
        int pause = Integer.parseInt(props.getProperty("pause", "1000"));
        
        try (ZContext context = new ZContext()) {
            // Create & bind the single PUB socket
            ZMQ.Socket pub = context.createSocket(SocketType.PUB);
            pub.bind(address);
            System.out.println("Environment PUB socket bound to " + address);
            
            for (int i = 0; i < maxLength; i++) {
                Thread.sleep(pause);

                System.err.println("Step: " + i);
                // For each function, send the i value if it exists
                for (String function : environmentFunctions) {

                    if (i < environmentFunctionsValues.get(function).size()) {
                        Map<String, String> payload = new HashMap<>();
                        payload.put(function, environmentFunctionsValues.get(function).get(i));

                        // Send to the right topic
                        // pub.sendMore(function);
                        // // Send the value
                        // pub.send(gson.toJson(payload));
                        // // Print the message
                        // System.out.println("Sent " + function + " value " + environmentFunctionsValues.get(function).get(i) + " to " + address + " at topic " + function);
                        
                    }
                }

                if (i == 1) {
                    // pub.sendMore("Client");
                    
                    Map test = new HashMap<>();
                    test.put("message", "input");
                    test.put("ventilatorType", "Volume");
                    test.put("value", "100");
                    
                    pub.send("Client " + gson.toJson(test));
                    System.out.println("Sent 100 to "  + address + " at topic Client");
                    
                }
                if (i == 2) {
                    // pub.sendMore("Client");
                    
                    Map test = new HashMap<>();
                    test.put("message", "input");
                    test.put("ventilatorType", "Volume");
                    test.put("value", "700");
                    
                    pub.send("Client " + gson.toJson(test));
                    System.out.println("Sent 400 to "  + address + " at topic Client");
                    
                }
                if (i == 3) {
                    // pub.sendMore("Client");
                    
                    Map test = new HashMap<String, String>();
                    test.put("message", "input");
                    test.put("ventilatorType", "Volume");
                    test.put("value", "2000");
                    
                    System.out.println("Test map " + test);
                    pub.send("Client " + gson.toJson(test));
                    System.out.println("Sent 1200 to "  + address + " at topic Client");

                }
            }
        } catch (Exception e) {
            System.err.println("An error occurred in the environment application: " + e.getMessage());
            e.printStackTrace();
        }
    }
}