package asmeta.asmeta_zeromq.producerconsumer2;


import asmeta.asmeta_zeromq.zeroMQW2;

public class producer {
    public static void main(String[] args) {
        zeroMQW2 producer = new zeroMQW2("../../zmq_config.properties");
        producer.run();
    }

}
