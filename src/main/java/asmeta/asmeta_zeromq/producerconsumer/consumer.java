package asmeta.asmeta_zeromq.producerconsumer;

import asmeta.asmeta_zeromq.zeroMQW;

public class consumer {
    public static void main(String[] args) {
        zeroMQW consumer = new zeroMQW("/zmq_config_consumer.properties");
        consumer.start();
    }
}
