package client;

import java.util.Iterator;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import thermostat.protos.GetTemperatureHistoryRequest;
import thermostat.protos.SetTargetTemperatureRequest;
import thermostat.protos.SmartThermostatGrpc;
import thermostat.protos.TemperatureReading;
import thermostat.protos.SetTargetTemperatureResponse;
import thermostat.protos.GetAverageTemperatureResponse;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;



public class ThermostatClient {
	
	private static Logger logger = Logger.getLogger(ThermostatClient.class.getName());
	
	
	public static void main(String[] args) throws InterruptedException {
        String host = "localhost";
        int port = 50051;
        
        ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port)
        		.usePlaintext()
        		.build();
        SmartThermostatGrpc.SmartThermostatBlockingStub blockingStub = SmartThermostatGrpc.newBlockingStub(channel);
        SmartThermostatGrpc.SmartThermostatStub asyncStub = SmartThermostatGrpc.newStub(channel);
        
        try {
        	//Unary RPC: Set TargetTemperature
        	double targetTemp = 22.5;
        	SetTargetTemperatureRequest request = SetTargetTemperatureRequest.newBuilder().setTargetTemp(targetTemp).build();
        	
        	//perform the RPC and capture response
        	SetTargetTemperatureResponse response = blockingStub.setTargetTemperature(request);
        	logger.info("SetTargetTemperature(" + targetTemp + ") → success=" + response.getSuccess());
        	
        	//Server streaming RPC: StreamTemperatureHistory
        	long now = System.currentTimeMillis();
        	GetTemperatureHistoryRequest histReq = GetTemperatureHistoryRequest.newBuilder()
        			.setStartTimestamp(now - 10_000) //10 seconds ago
        			.setEndTimestamp(now)
        			.build();
        	
        	//invoke the streaming RPC, returns temperature reading
        	Iterator<TemperatureReading> readings = blockingStub.streamTemperatureHistory(histReq);
        	logger.info("Temperature history: ");
        	while (readings.hasNext()) {
        		TemperatureReading r = readings.next();
        		logger.info(String.format(" time=%d, temp=%.2f°C", r.getTimestamp(), r.getTemperature()));
        	}
        	
        	//Client streaming RPC: GetAverageTemperature
        	//use a latch to wait for async response complete
        	CountDownLatch latch = new CountDownLatch(1);
        	
        	//Define response observer to handle servers reply
        	StreamObserver<GetAverageTemperatureResponse> respObserver = new StreamObserver<GetAverageTemperatureResponse>() {
        		@Override
        		public void onNext(GetAverageTemperatureResponse resp) {
        			//log computed average
        			logger.info(String.format("Average=%.2f°C",resp.getAverageTemp()));
        		}
        		@Override
        		public void onError(Throwable t) {
        			//log errors amd count down latch to free main thread
        			logger.log(Level.WARNING,"getAverageTemperature failed: {0}",StatusRuntimeException.class.cast(t));
        			latch.countDown();
        		}
        		@Override
        		public void onCompleted() {
        			//the server has completed sending response
        			logger.info("getAverageTemperature completed successfully");
        			latch.countDown();
        		}
        	};
        	
        	//Obtain request observer for the readings
        	StreamObserver<TemperatureReading> reqObserver = asyncStub.getAverageTemperature(respObserver);
        	//send sample temperature readings
        	for (double temp : new double[]{21.0, 22.0, 23.5}) {
        		TemperatureReading tr = TemperatureReading.newBuilder()
        				.setTemperature(temp)
        				.setTimestamp(System.currentTimeMillis())
        				.build();
        		reqObserver.onNext(tr);
        		Thread.sleep(100);//delay between readings
        	}
        	reqObserver.onCompleted();
        	
        	//wait 1 min for server response
        	if(!latch.await(1,  TimeUnit.MINUTES)) {
        		logger.warning("getAverageTemperature can't finish within 1 minute");
        	}
        	
        } catch (StatusRuntimeException e) {
        	//catch and log RPC level errors
        	logger.log(Level.WARNING, "RPC failed: {0}", e.getStatus());
        } finally {
        	//shut down channel
        	channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
        }
	}
	
}