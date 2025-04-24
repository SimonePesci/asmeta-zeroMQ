package asmeta.asmeta_zeromq;



public class Starter {
    
    public static void main(String[] args) {
        zeroMQW producer = new zeroMQW("/zmq_config.properties");
        zeroMQW consumer = new zeroMQW("/zmq_config_consumer.properties");

    }
}
