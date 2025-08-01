package client;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import thermostat.protos.GetTemperatureHistoryRequest;
import thermostat.protos.SetTargetTemperatureRequest;
import thermostat.protos.SmartThermostatGrpc;
import thermostat.protos.TemperatureReading;
import thermostat.protos.SetTargetTemperatureResponse;
import thermostat.protos.GetAverageTemperatureResponse;


public class ThermostatClient {
	
	private static Logger logger = Logger.getLogger(ThermostatClient.class.getName());
	
	//gRPC channel and stubs
    static String host = "localhost";
    static int port = 50051;
        
    private static final ManagedChannel channel;
    private static final SmartThermostatGrpc.SmartThermostatBlockingStub blockingStub;
    private static final SmartThermostatGrpc.SmartThermostatStub asyncStub;
    
    // Static initializer to set up channel and stubs
    static {
        channel = ManagedChannelBuilder.forAddress(host, port)
                .usePlaintext()
                .build();
        blockingStub = SmartThermostatGrpc.newBlockingStub(channel);
        asyncStub    = SmartThermostatGrpc.newStub(channel);
    }
    
    //shutdown method
    public static void shutdown() {
    	channel.shutdown();
    	try {
    		if (!channel.awaitTermination(5,  TimeUnit.SECONDS)) {
    			channel.shutdownNow();
    		}
    	}catch(InterruptedException e) {
    		Thread.currentThread().interrupt();    	
    	}
    }   
    
        
    //Unary RPC: Set TargetTemperature
    public static boolean setTarget(double temp) {
    	SetTargetTemperatureRequest request = SetTargetTemperatureRequest.newBuilder().setTargetTemp(temp).build();
    	try {
    		SetTargetTemperatureResponse response = blockingStub.setTargetTemperature(request);
    		return response.getSuccess();
    	}catch(StatusRuntimeException e) {
    		logger.warning("setTarget RPC failed: " + e.getStatus());
    		return false;
    	}
    }
    		
    //Server streaming RPC: StreamTemperatureHistory	
    public static List<TemperatureReading> getHistory(long start, long end) {
    	GetTemperatureHistoryRequest request = GetTemperatureHistoryRequest.newBuilder()
    			.setStartTimestamp(start) 
    			.setEndTimestamp(end)
    			.build();
    	List<TemperatureReading> readings = new ArrayList<>();
    	try {
    		Iterator<TemperatureReading> iter = blockingStub.streamTemperatureHistory(request);
    		iter.forEachRemaining(readings::add);
    	}catch(StatusRuntimeException e) {
    		logger.warning("getHistory RPC failed: " + e.getStatus());
    	}
    	return readings;
    }
    		
    //Client streaming RPC: GetAverageTemperature
    public static double getAverage(long start, long end) {
    	CountDownLatch latch = new CountDownLatch(1);
    	AtomicReference<Double> avgRef = new AtomicReference<>(Double.NaN);
    	
    	StreamObserver<GetAverageTemperatureResponse> respObserver = new StreamObserver<GetAverageTemperatureResponse>() {
    		@Override
    		public void onNext(GetAverageTemperatureResponse response) {
    			avgRef.set(response.getAverageTemp());
    		}
    		@Override
    		public void onError(Throwable t) {
    			logger.warning("getAverage RPC failed: " + t);
    			latch.countDown();
    		}
    		@Override
    		public void onCompleted() {
    			latch.countDown();
    		}
    	};
    	
    	try {
    		//request observer for sending temperature readings
    		StreamObserver<TemperatureReading> reqObserver = asyncStub.getAverageTemperature(respObserver);
    		
    		//generate one reading per second between start and end
    		for(long ts = start; ts <= end; ts +=1000) {
    			TemperatureReading reading = TemperatureReading.newBuilder()
    					.setTimestamp(ts)
    					.setTemperature(20.0 + Math.random()*0.5)
    					.build();
    			reqObserver.onNext(reading);
    		}
    		reqObserver.onCompleted();
    		
    		//wait for server response or timeout
    		if(!latch.await(5,  TimeUnit.SECONDS)) {
    			logger.warning("getAverage RPC timeout");
    		}
    	}catch(InterruptedException e) {
    		Thread.currentThread().interrupt();
    	}catch(StatusRuntimeException e) {
    		logger.warning("getAverage RPC failed: " + e.getStatus());
    	}
    	return avgRef.get();
    }
}
