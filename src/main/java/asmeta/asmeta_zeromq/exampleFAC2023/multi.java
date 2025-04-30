package asmeta.asmeta_zeromq.exampleFAC2023;

import asmeta.asmeta_zeromq.zeroMQW;

public class multi {

    public static void main(String[] args) {
        zeroMQW multi = new zeroMQW("/exampleFAC2023-config/zmq_config_multi.properties");
        multi.start();
    }

}
