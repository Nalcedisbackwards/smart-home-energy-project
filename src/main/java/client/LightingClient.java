/*
 * LightingClient.java
 *
 * Client implementation for the SmartLighting gRPC service. 
 * Establishes a channel to the LightingServer. 
 * Offers methods for each RPC.
 * Includes proper shutdown and error handling.
 */

package client;

import java.util.ArrayList;
import java.util.Collections;
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
import lighting.protos.GetCurrentBrightnessRequest;
import lighting.protos.GetCurrentBrightnessResponse;
import lighting.protos.StreamAmbientLightDataRequest;
import lighting.protos.AmbientLightReading;
import lighting.protos.LightUsageStat;
import lighting.protos.UploadLightUsageResponse;
import lighting.protos.AdjustBrightnessRequest;
import lighting.protos.AdjustBrightnessResponse;
import lighting.protos.SmartLightingServiceGrpc;
import javax.jmdns.JmDNS;
import javax.jmdns.ServiceInfo;
import java.io.IOException;
import java.net.InetAddress;

public class LightingClient {
    private static final Logger logger = Logger.getLogger(LightingClient.class.getName());

    //Host and port for connecting to the gRPC server if jmDNS fails
    static String host = "localhost";
    static int port = 50053;
    
    //Channel and stub declarations
    private static final ManagedChannel channel;
    private static final SmartLightingServiceGrpc.SmartLightingServiceBlockingStub blockingStub;
    private static final SmartLightingServiceGrpc.SmartLightingServiceStub asyncStub;
    
    //Static initializer to set up channel and stubs
    static {
    	ManagedChannel tmpChannel;
    	try {
    	    //Create jmDNS and lookup the service
    	    JmDNS jmdns = JmDNS.create(InetAddress.getLocalHost());
    	    ServiceInfo info = jmdns.getServiceInfo(
    	        "_smartlighting._grpc._tcp.local.",  
    	        "SmartLightingService",
    	        5000 //Timeout
    	    );
    	    if (info == null) throw new IOException("SmartLightingService not found");

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
        blockingStub = SmartLightingServiceGrpc.newBlockingStub(channel);
        asyncStub    = SmartLightingServiceGrpc.newStub(channel);
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
    
    //Unary RPC: Get current brightness
    public static GetCurrentBrightnessResponse getCurrentBrightness(String zoneId) {
    	
    	//Build request with zone ID
        GetCurrentBrightnessRequest req = GetCurrentBrightnessRequest.newBuilder()
        		.setZoneId(zoneId)
                .build();
        try {
        	//Blocking call
            return blockingStub.getCurrentBrightness(req);
        } catch (StatusRuntimeException e) {
            logger.warning("getCurrentBrightness RPC failed: " + e.getStatus()); //Error handling
            return null;
        }
    }

    //Server streaming RPC: Get ambient light readings
    public static Iterator<AmbientLightReading> streamAmbientLightData(String zone) {
        StreamAmbientLightDataRequest req = StreamAmbientLightDataRequest.newBuilder()
            .setZoneId(zone)
            .build();
        try {
        	//Return iterator
        	return blockingStub.streamAmbientLightData(req);
        }catch(StatusRuntimeException e) {
        	logger.warning("streamAmbientLightData RPC falied: " + e.getStatus()); //Error handling
        	//Return empty iterator on failure
        	return Collections.emptyIterator();
        }
    }
    
    //Client streaming RPC: upload usage stats
    public static double uploadUsageStats(List<LightUsageStat> stats) {
        CountDownLatch latch = new CountDownLatch(1);	//Synchronization aid
        AtomicReference<Double> totalRef = new AtomicReference<>(0.0); //To capture response
        
        //Response observer to handle server's reply
        StreamObserver<UploadLightUsageResponse> respObs = new StreamObserver<UploadLightUsageResponse>() {
            @Override 
            public void onNext(UploadLightUsageResponse resp) {
            	totalRef.set(resp.getTotalEnergyKw());
            }
            @Override 
            public void onError(Throwable t) {
                logger.warning("uploadLightUsageStats RPC failed: " + t.getMessage()); //Error handling
                latch.countDown();
            }
            @Override 
            public void onCompleted() {
                latch.countDown();
            }
        };

        try {
        	//Obtain request observer and send each stat
            StreamObserver<LightUsageStat> reqObs = asyncStub.uploadLightUsageStats(respObs);
            for (LightUsageStat stat : stats) {
                reqObs.onNext(stat);
            }
            reqObs.onCompleted();
            //Wait for response or timeout after 5 seconds
            if (!latch.await(5, TimeUnit.SECONDS)) {
                logger.warning("uploadLightUsageStats RPC timeout");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); //Error handling
        }
        return totalRef.get();
    }
    
    //Bidirectional streaming RPC: adjust brightness and receive lux
    public static List<AdjustBrightnessResponse> adjustBrightness(
            List<AdjustBrightnessRequest> requests) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        List<AdjustBrightnessResponse> responses = new ArrayList<>();
        
        //Response observer to collect incoming messages
        StreamObserver<AdjustBrightnessResponse> respObs = new StreamObserver<AdjustBrightnessResponse>() {
            @Override 
            public void onNext(AdjustBrightnessResponse resp) {
                responses.add(resp);
            }
            @Override 
            public void onError(Throwable t) {
                logger.warning("adjustBrightness RPC failed: " + t.getMessage()); //Error handling
                latch.countDown();
            }
            @Override 
            public void onCompleted() {
                latch.countDown();
            }
        };
        
        //Obtain request observer and send each adjustment
        StreamObserver<AdjustBrightnessRequest> reqObs = asyncStub.adjustBrightness(respObs);
        for (AdjustBrightnessRequest req : requests) {
            reqObs.onNext(req);
        }
        reqObs.onCompleted();
        
        //Wait for server to complete or timeout after 10 seconds
        if (!latch.await(10, TimeUnit.SECONDS)) {
            logger.warning("adjustBrightness RPC timeout"); //Error handling
        }
        return responses;
    }
}