package asmeta.asmeta_zeromq;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;

import com.google.gson.Gson;

@SpringBootTest(classes=zeroMQWrapperFinal.class)
class AsmetaZeromqApplicationTests {

	private final Gson gson = new Gson();

	@Test
	void testRunningModelsAction() {
		try (ZContext context = new ZContext()) {
			ZMQ.Socket triggerPublisher = context.createSocket(SocketType.PUB);
			String triggerPubAddress = "tcp://*:5562";
			triggerPublisher.bind(triggerPubAddress);
			System.out.println("Test Trigger PUB socket bound to " + triggerPubAddress);

			ZMQ.Socket responseSubscriber = context.createSocket(SocketType.SUB);
			String responseSubAddress = "tcp://localhost:5556";
			responseSubscriber.connect(responseSubAddress);
			responseSubscriber.subscribe("".getBytes(ZMQ.CHARSET));
			System.out.println("Test Response SUB socket connected to " + responseSubAddress);

			System.out.println("Waiting for connections...");
			Thread.sleep(2000);

			Map<String, String> monitored = new HashMap<>();
			monitored.put("trigger", "1");
			String jsonMessage = gson.toJson(monitored);

			System.out.println("Sending trigger: " + jsonMessage);
			triggerPublisher.send(jsonMessage);

			System.out.println("Waiting for response...");
			String respJson = responseSubscriber.recvStr();
			System.out.println("Received response: " + respJson);
			Map<?, ?> response = gson.fromJson(respJson, Map.class);

			assertTrue(response.containsKey("outputValues") || response.containsKey("asm_status"));

			triggerPublisher.close();
			responseSubscriber.close();

		} catch (Exception e) {
			System.err.println("Test failed with exception:");
			e.printStackTrace();
			assertTrue(false, "Test threw an exception: " + e.getMessage());
		}
	}

}
