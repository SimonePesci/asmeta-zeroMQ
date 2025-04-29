package asmeta.asmeta_zeromq.producerconsumer;

import asmeta.asmeta_zeromq.zeroMQW;

public class producer {

    public static void main(String[] args) {
        zeroMQW producer = new zeroMQW("../../zmq_config.properties");
        producer.start();


    }

}
