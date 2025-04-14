package asmeta.asmeta_zeromq;

import java.io.File;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.asmeta.runtime_container.RunOutput;
import org.asmeta.runtime_container.SimulationContainer;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;

import com.google.gson.Gson;

@SpringBootApplication
public class AsmetaZeromqApplication {

	public static SimulationContainer sim;

    static final String modelsFolderPath = "src/main/resources/models/";
    static final String librariesFolderPath = "src/main/resources/libraries/";

    private static final Gson gson = new Gson();

    public static void main(String[] args) {
        sim = new SimulationContainer();
        sim.init(10);
        SpringApplication.run(AsmetaZeromqApplication.class, args);

        try (ZContext context = new ZContext()) {
            // Create PUB socket for sending responses
            ZMQ.Socket publisher = context.createSocket(ZMQ.PUB);
            publisher.bind("tcp://*:5556");
            
            // Create SUB socket for receiving requests
            ZMQ.Socket subscriber = context.createSocket(ZMQ.SUB);
            subscriber.bind("tcp://*:5555");
            subscriber.subscribe("".getBytes(ZMQ.CHARSET)); // Subscribe to all messages

            System.out.println("ZeroMQ PUB/SUB Server is running");
            System.out.println("SUB listening on port 5555");
            System.out.println("PUB publishing on port 5556");

            while (!Thread.currentThread().isInterrupted()) {
                // Receive message
                String request = subscriber.recvStr(0).trim();
                System.out.println("Received request: " + request);

                Map<String, Object> reqMap = gson.fromJson(request, Map.class);
                String action = (String) reqMap.get("action");
                Map<String, Object> response = new HashMap<>();

                try {
                    if (action == null) {
                        response.put("error", "Action not specified");
                        break;
                    }
                    switch (action) {
                        case "running-models":
                            response = handleRunningModels();
                            break;
                        case "get-model-status": {
                            int id = ((Double) reqMap.get("id")).intValue();
                            response = handleGetModelStatus(id);
                            break;
                        }
                        case "start": {
                            String name = (String) reqMap.get("name");
                            response = handleStart(name);
                            break;
                        }
                        case "step": {
                            int id = ((Double) reqMap.get("id")).intValue();
                            @SuppressWarnings("unchecked")
                            Map<String, String> monitoredVariables = (Map<String, String>) reqMap.get("monitoredVariables");
                            response = handleStep(id, monitoredVariables);
                            break;
                        }
                        case "step-single-input": {
                            int id = ((Double) reqMap.get("id")).intValue();
                            String monitoredVariable = (String) reqMap.get("monitoredVariable");
                            response = handleStepSingleInput(id, monitoredVariable);
                            break;
                        }
                        case "run-until-empty": {
                            int id = ((Double) reqMap.get("id")).intValue();
                            @SuppressWarnings("unchecked")
                            Map<String, String> monitoredVariables = (Map<String, String>) reqMap.get("monitoredVariables");
                            response = handleRunUntilEmpty(id, monitoredVariables);
                            break;
                        }
                        case "stop-model": {
                            int id = ((Double) reqMap.get("id")).intValue();
                            response = handleStopModel(id);
                            break;
                        }
                        case "model-list": {
                            response = handleModelList();
                            break;
                        }
                        case "upload-model": {
                            // we pass the whole request map so that the fileName and content can be extracted
                            response = handleUploadModel(reqMap);
                            break;
                        }
                        default:
                            response.put("error", "Unknown action: " + action);
                            break;
                    }

                } catch (Exception e) {
                    response.put("error", e.getMessage());
                    e.printStackTrace();
                }

                // Convert the response map to a JSON string and send it.
                String respJson = gson.toJson(response);
                System.out.println("Sending response: " + respJson);
                publisher.send(respJson);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    // Returns all models running
    private static Map<String, Object> handleRunningModels() {
        Map<Integer, String> loadedModels = sim.getLoadedIDs();
        Map<Integer, String> outputModels = new HashMap<>();
        for (Map.Entry<Integer, String> entry : loadedModels.entrySet()) {
            outputModels.put(entry.getKey(), Paths.get(entry.getValue()).getFileName().toString());
        }
        Map<String, Object> returnVal = new HashMap<>();
        returnVal.put("models", outputModels);
        return returnVal;
    }

    // Returns the model status
    private static Map<String, Object> handleGetModelStatus(int id) {
        RunOutput lastStepOut = sim.getCurrentState(id);
        Map<String, Object> returnVal = new HashMap<>();
        returnVal.put("id", id);
        returnVal.put("runOutput", lastStepOut);
        String modelName = sim.getAsmetaModel(id).getModelName();
        returnVal.put("modelName", modelName);
        return returnVal;
    }

    // Starts a model
    private static Map<String, Object> handleStart(String name) {
        String modelPath = modelsFolderPath + name;
        int id = sim.startExecution(modelPath);
        Map<String, Object> returnVal = new HashMap<>();
        returnVal.put("id", id);
        return returnVal;
    }

    // Makes a step
    private static Map<String, Object> handleStep(int id, Map<String, String> monitoredVariables) {
        RunOutput stepOutput;
        if (monitoredVariables == null || monitoredVariables.isEmpty() ) {
            stepOutput = sim.runStep(id);
        } else {
            stepOutput = sim.runStep(id, monitoredVariables);
        }
        Map<String, Object> returnVal = new HashMap<>();
        returnVal.put("id", id);
        returnVal.put("runOutput", stepOutput);
        String modelName = sim.getAsmetaModel(id).getModelName();
        System.out.println("Model Name:" + modelName + "");
        List<String> monitored = sim.getMonitored(modelsFolderPath + modelName);
        returnVal.put("monitored", monitored);
        return returnVal;
    }

    // 
    private static Map<String, Object> handleStepSingleInput(int id, String monitoredVariable) {
        Map<String, String> monitoredInput = prepareInput(monitoredVariable);
        RunOutput stepOutput;
        if (monitoredInput.isEmpty()) {
            stepOutput = sim.runStep(id);
        } else {
            stepOutput = sim.runStep(id, monitoredInput);
        }

        Map<String, Object> returnVal = new HashMap<>();
        returnVal.put("id", id);
        returnVal.put("runOutput", stepOutput);
        String modelName = sim.getAsmetaModel(id).getModelName();
        System.out.print(modelName + ": Model Name");
        List<String> monitored = sim.getMonitored(modelName + modelName);
        returnVal.put("monitored", monitored);
        return returnVal;
    }

    // Makes the model run until it stops
    private static Map<String, Object> handleRunUntilEmpty(int id, Map<String, String> monitoredVariables ) {
        RunOutput stepOutput;
        if (monitoredVariables == null || monitoredVariables.isEmpty()) {
            stepOutput = sim.runUntilEmpty(id);
        } else {
            stepOutput = sim.runUntilEmpty(id, monitoredVariables);
        }
        Map<String, Object> returnVal = new HashMap<>();
        returnVal.put("id", id);
        returnVal.put("runOutput", stepOutput);
        String modelName = sim.getAsmetaModel(id).getModelName();
        List<String> monitored = sim.getMonitored(modelsFolderPath + modelName);
        returnVal.put("monitored", monitored);
        return returnVal;
    }

    // Stops a running model
    private static Map<String, Object> handleStopModel(int id) {
        int result = sim.stopExecution(id);
        Map<String, Object> returnVal = new HashMap<>();
        returnVal.put("status", result > 0);
        return returnVal;
    }

    // Takes a string ("var1 val1 var2 val2") and return the pairs ->
    // var1 -> val1 | var2 -> val2 in a Map
    private static Map<String, String> prepareInput(String cmd) {
        Map<String, String> data = new HashMap<>();
        String[] input = cmd.split(" ");
        for (int i = 0; i < input.length; i++) {
            data.put(input[i], input[++i]);
        }
        return data;
    }

    private static Map<String, Object> handleModelList() {
        Map<String, Object> returnVal = new HashMap<>();
    
        List<String> models = new ArrayList<>();
        File modelsDir = new File(modelsFolderPath);
        if (modelsDir.exists() && modelsDir.isDirectory()) {
            File[] files = modelsDir.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isFile()) {
                        models.add(file.getName());
                    }
                }
            }
        }
    
        List<String> libraries = new ArrayList<>();
        File librariesDir = new File(librariesFolderPath);
        if (librariesDir.exists() && librariesDir.isDirectory()) {
            File[] files = librariesDir.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isFile()) {
                        libraries.add(file.getName());
                    }
                }
            }
        }
    
        Collections.sort(models, String.CASE_INSENSITIVE_ORDER);
        Collections.sort(libraries, String.CASE_INSENSITIVE_ORDER);
    
        returnVal.put("models", models);
        returnVal.put("libraries", libraries);
    
        return returnVal;
    }

    // Upload of file not supported in zeroMQ
    private static Map<String, Object> handleUploadModel(Map<String, Object> reqMap) {
        // TODO: Upload of file not supported in zeroMQ
        return new HashMap<>();
    }
}

