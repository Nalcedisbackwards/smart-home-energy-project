/*
 * ThermostatClient.java
 *
 * Client implementation for the SmartThermostat gRPC service. 
 * Establishes a channel to the ThermostatServer. 
 * Offers methods for each RPC.
 * Includes proper shutdown and error handling.
 */

package client;

import java.io.IOException;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

import javax.jmdns.JmDNS;
import javax.jmdns.ServiceInfo;

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
	
	//Host and port for connecting to the gRPC server if jmDNS fails
    static String host = "localhost";
    static int port = 50051;
    
    //Channel and stub declarations
    private static final ManagedChannel channel;
    private static final SmartThermostatGrpc.SmartThermostatBlockingStub blockingStub;
    private static final SmartThermostatGrpc.SmartThermostatStub asyncStub;
    
  //Static initializer to set up channel and stubs
    static {
    	ManagedChannel tmpChannel;
    	try {
    	    //Create jmDNS and lookup the service
    	    JmDNS jmdns = JmDNS.create(InetAddress.getLocalHost());
    	    ServiceInfo info = jmdns.getServiceInfo(
    	        "_thermostat._grpc._tcp.local.",  
    	        "ThermostatService",
    	        5000 //Timeout
    	    );
    	    if (info == null) throw new IOException("SmartThermostatService not found");

    	    //Pull host and port from the record
            tmpChannel = ManagedChannelBuilder
                    .forAddress(info.getHostAddresses()[0], info.getPort())
                    .usePlaintext()
                    .build();
    	}catch(Exception e) {
    		logger.warning("jmDNS lookup failed, defaulting to localhost:PORT — " + e.getMessage());
    	    //Fallback to localhost
    		tmpChannel = ManagedChannelBuilder
    	        .forAddress(host, port)
    	        .usePlaintext()
    	        .build();
    	}
    	
    	channel = tmpChannel;
        blockingStub = SmartThermostatGrpc.newBlockingStub(channel);
        asyncStub    = SmartThermostatGrpc.newStub(channel);
    }
    
    //Shutdown method
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
    
        
    //Unary RPC: Set target temperature
    public static boolean setTarget(double temp) {
    	//Build request with new target temperature
    	SetTargetTemperatureRequest request = SetTargetTemperatureRequest.newBuilder().setTargetTemp(temp).build();
    	try {
    		//Blocking call
    		SetTargetTemperatureResponse response = blockingStub.setTargetTemperature(request);
    		return response.getSuccess();
    	}catch(StatusRuntimeException e) {
    		logger.warning("setTarget RPC failed: " + e.getStatus()); //Error handling
    		return false;
    	}
    }
    		
    //Server streaming RPC: Stream temperature history	
    public static List<TemperatureReading> getHistory(long start, long end) {
    	//Build request with start and end timestamps
    	GetTemperatureHistoryRequest request = GetTemperatureHistoryRequest.newBuilder()
    			.setStartTimestamp(start) 
    			.setEndTimestamp(end)
    			.build();
    	List<TemperatureReading> readings = new ArrayList<>();
    	try {
    		//Iterate through all readings
    		Iterator<TemperatureReading> iter = blockingStub.streamTemperatureHistory(request);
    		iter.forEachRemaining(readings::add); //Add each reading to the list
    	}catch(StatusRuntimeException e) {
    		logger.warning("getHistory RPC failed: " + e.getStatus()); //Error handling
    	}
    	return readings;
    }
    		
    //Client streaming RPC: Get average temperature
    public static double getAverage(long start, long end) {
    	
    	//Latch to wait for the server's single response
    	CountDownLatch latch = new CountDownLatch(1);
    	
    	//Atomic reference to capture the average from asynchronous callback
    	AtomicReference<Double> avgRef = new AtomicReference<>(Double.NaN);
    	
    	//Response observer to handle server's reply
    	StreamObserver<GetAverageTemperatureResponse> respObserver = new StreamObserver<GetAverageTemperatureResponse>() {
    		@Override
    		public void onNext(GetAverageTemperatureResponse response) {
    			avgRef.set(response.getAverageTemp()); //Capture the average temperature
    		}
    		@Override
    		public void onError(Throwable t) {
    			logger.warning("getAverage RPC failed: " + t); //Error handling
    			latch.countDown();
    		}
    		@Override
    		public void onCompleted() {
    			latch.countDown();
    		}
    	};
    	
    	try {
    		//Request observer for sending temperature readings
    		StreamObserver<TemperatureReading> reqObserver = asyncStub.getAverageTemperature(respObserver);
    		
    		//Generate one reading per second between start and end
    		for(long ts = start; ts <= end; ts +=1000) {
    			TemperatureReading reading = TemperatureReading.newBuilder()
    					.setTimestamp(ts)
    					.setTemperature(20.0 + Math.random()*0.5) //Simulated noise around 20°C
    					.build();
    			reqObserver.onNext(reading); //Send each reading
    		}
    		reqObserver.onCompleted(); //End stream
    		
    		//Wait for server response or timeout
    		if(!latch.await(5,  TimeUnit.SECONDS)) {
    			logger.warning("getAverage RPC timeout");
    		}
    	}catch(InterruptedException e) {
    		Thread.currentThread().interrupt();
    	}catch(StatusRuntimeException e) {
    		logger.warning("getAverage RPC failed: " + e.getStatus());
    	}
    	return avgRef.get(); //Return average or NaN
    }
}
