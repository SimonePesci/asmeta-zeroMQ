package asmeta.asmeta_zeromq.trafficLightSimCoSimCross;

import asmeta.asmeta_zeromq.zeroMQW;

public class TrafficLightTram {
    public static void main(String[] args) {
        String configFile = "/trafficLightSimCoSimCross-config/zmq_config_trafficlightTram.properties";
        System.out.println("Starting TrafficLightTram ASM with config: " + configFile);
        zeroMQW trafficLightTramInstance = new zeroMQW(configFile);
        trafficLightTramInstance.setName("TrafficLightTram-ASM-Thread");
        trafficLightTramInstance.start();
    }
}