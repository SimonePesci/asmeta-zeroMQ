package asmeta.asmeta_zeromq.producerconsumer2;

import asmeta.asmeta_zeromq.zeroMQW2;

public class consumer {
    public static void main(String[] args) {
        zeroMQW2 consumer = new zeroMQW2("../../zmq_config_consumer.properties");
        consumer.run();
    }
}
