package asmeta.asmeta_zeromq;

import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.asmeta.runtime_container.RunOutput;
import org.asmeta.runtime_container.SimulationContainer;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;

import com.google.gson.Gson;

public class AsmetaZeromqMain {
    // Declare the SimulationContainer and Gson as static fields.
    private static final SimulationContainer sim = new SimulationContainer();
    private static final Gson gson = new Gson();

    static final String modelsFolderPath = "src/main/resources/models/";
    static final String librariesFolderPath = "src/main/resources/libraries/";

    public static void main(String[] args) {
        sim.init(10);
        

        try (ZContext context = new ZContext()) {
            ZMQ.Socket socket = context.createSocket(ZMQ.REP);
            socket.bind("tcp://*:5555");
            System.out.println("ZeroMQ Server is running on port 5555");

            while (!Thread.currentThread().isInterrupted()) {
                // Receive message
                String request = socket.recvStr(0).trim();
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
                socket.send(respJson);
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
}
